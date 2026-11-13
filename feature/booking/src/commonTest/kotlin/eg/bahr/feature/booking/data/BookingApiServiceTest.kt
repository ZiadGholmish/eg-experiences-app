package eg.bahr.feature.booking.data

import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.feature.booking.model.GuestRequest
import eg.bahr.feature.booking.model.PayerRequest
import eg.bahr.feature.booking.model.PlaceHoldRequest
import eg.bahr.feature.booking.model.StartPaymentRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * `placeHold` and the two reads of the date + party screen through the app's real client on a
 * MockEngine, answered with the contract's examples (`HeldSeats`, `ApiError`) and the seeded trip.
 */
class BookingApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): BookingApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        return BookingApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "ar" })
    }

    private val request =
        PlaceHoldRequest(
            departureId = "0199c3a0-5eed-7000-8000-000000000601",
            partySize = 2,
            guest = GuestRequest(name = "Nada Hassan", phone = "+201001234567"),
        )

    @Test
    fun `places a hold with the contract's body and decodes the held seats`() =
        runTest {
            val result = service(HELD, HttpStatusCode.Created).placeHold(request)

            val sent = requests.single()
            assertEquals(HttpMethod.Post, sent.method)
            assertEquals("/api/v1/bookings", sent.url.encodedPath)
            val body = Json.parseToJsonElement((sent.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            assertEquals("0199c3a0-5eed-7000-8000-000000000601", body["departureId"]?.jsonPrimitive?.content)
            assertEquals("2", body["partySize"]?.jsonPrimitive?.content)
            val guest = body["guest"] as JsonObject
            assertEquals("Nada Hassan", guest["name"]?.jsonPrimitive?.content)
            assertEquals("+201001234567", guest["phone"]?.jsonPrimitive?.content)
            // Absent, not null: the contract defaults it to Accept-Language.
            assertFalse("locale" in guest)

            assertIs<AppResult.Success<*>>(result)
            val held = (result as AppResult.Success).data
            assertEquals("BRL-7K4M2X9P", held.ref)
            assertEquals(MoneyDto(900, "EGP"), held.total)
            assertEquals("2026-10-09T22:42:30.549567+03:00", held.holdExpiresAt)
            assertEquals("2026-10-09T22:27:30.549567+03:00", held.serverNow)
            assertEquals(10, held.seatsRemaining)
        }

    @Test
    fun `a hold carries the install's id and its kept secret - only the id when none is kept`() =
        runTest {
            val ids = DeviceIdSource { DEVICE_ID }
            val kept = mutableMapOf(DEVICE_ID to "install-secret-1")
            val secrets =
                object : InstallSecretStore {
                    override suspend fun read(deviceId: String) = kept[deviceId]

                    override suspend fun write(
                        deviceId: String,
                        secret: String?,
                    ) = Unit
                }
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(HELD, HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            val api =
                BookingApiService(
                    apiHttpClient(
                        engine,
                        ApiConfig(BASE_URL, isDebug = false),
                        // The device-id plugin rides on the session's (signed out here).
                        session = AuthSession(NoSession),
                        deviceIds = ids,
                        installSecrets = secrets,
                    ) { "ar" },
                )

            api.placeHold(request)
            kept.clear()
            api.placeHold(request)

            val (proven, bare) = requests
            assertEquals(DEVICE_ID, proven.headers["X-Device-Id"])
            assertEquals("install-secret-1", proven.headers["X-Install-Secret"], "openapi HoldInstallSecret (M3-B13a)")
            assertEquals(DEVICE_ID, bare.headers["X-Device-Id"])
            assertNull(bare.headers["X-Install-Secret"], "never sent empty")
        }

    @Test
    fun `no seats is a failure carrying the contract's code`() =
        runTest {
            val result = service(NO_SEATS, HttpStatusCode.Conflict).placeHold(request)

            assertIs<AppResult.Failure>(result)
            val error = result.error
            assertIs<AppError.Api>(error)
            assertEquals(ApiErrorCodes.NO_SEATS_AVAILABLE, error.code)
        }

    @Test
    fun `reads the trip and its dates by slug`() =
        runTest {
            val trip = service(TRIP).tripBySlug("burullus-dawn")
            val dates = service(DATES).departuresFor("burullus-dawn")

            assertEquals(
                listOf("/api/v1/trips/burullus-dawn", "/api/v1/trips/burullus-dawn/departures"),
                requests.map { it.url.encodedPath },
            )
            val detail = (trip as AppResult.Success).data
            assertEquals(6, detail.policy?.maxPartySize)
            assertEquals(MoneyDto(450, "EGP"), detail.price)
            val list = (dates as AppResult.Success).data
            assertEquals(listOf(null, "SOLD_OUT"), list.map { it.unavailableReason })
        }

    @Test
    fun `reads a held booking with the guest's phone encoded plus as percent-2B`() =
        runTest {
            val result = service(BOOKING_HELD).heldBooking("BRL-Y4GHX1PW", "+201000000777")

            val sent = requests.single()
            assertEquals(HttpMethod.Get, sent.method)
            assertEquals("/api/v1/bookings/BRL-Y4GHX1PW", sent.url.encodedPath)
            // A bare + in a query decodes as a space; the server compares digits, so it would not notice.
            assertEquals("phone=%2B201000000777", sent.url.encodedQuery)

            val booking = (result as AppResult.Success).data
            assertEquals("HELD", booking.status)
            assertEquals("2026-10-09T23:20:40.39219+03:00", booking.holdExpiresAt)
            assertEquals("2026-10-09T23:05:40.453816+03:00", booking.serverNow)
            assertEquals("Dawn on Lake Burullus", booking.trip?.title)
            assertEquals("Abdel Moneim Riad", booking.departure?.placeName)
            assertEquals("05:00", booking.departure?.timeLocal)
            assertEquals(1, booking.partySize)
            assertEquals(MoneyDto(450, "EGP"), booking.total)
        }

    @Test
    fun `a released hold reads without a deadline`() =
        runTest {
            val booking = (service(BOOKING_CANCELLED).heldBooking("BRL-Y4GHX1PW", "+201000000777") as AppResult.Success).data

            assertEquals("CANCELLED", booking.status)
            assertEquals(null, booking.holdExpiresAt)
        }

    @Test
    fun `releases a hold with a 204 that has no body and no content type`() =
        runTest {
            val engine =
                MockEngine { request ->
                    requests += request
                    // Exactly as the local api answers: 204, no body, no Content-Type.
                    respond("", HttpStatusCode.NoContent)
                }
            val api = BookingApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "ar" })

            val result = api.releaseHold("BRL-Y4GHX1PW", "+201000000777")

            assertIs<AppResult.Success<Unit>>(result)
            val sent = requests.single()
            assertEquals(HttpMethod.Delete, sent.method)
            assertEquals("/api/v1/bookings/BRL-Y4GHX1PW/hold", sent.url.encodedPath)
            assertEquals("phone=%2B201000000777", sent.url.encodedQuery)
        }

    @Test
    fun `releasing a hold whose payment started is a conflict`() =
        runTest {
            val result = service(CONFLICT, HttpStatusCode.Conflict).releaseHold("BRL-Y4GHX1PW", "+201000000777")

            val error = (result as AppResult.Failure).error
            assertIs<AppError.Api>(error)
            assertEquals(ApiErrorCodes.CONFLICT, error.code)
        }

    @Test
    fun `starts a payment with the phone in the body and decodes the hosted checkout`() =
        runTest {
            val request =
                StartPaymentRequest(
                    method = "vodafone_cash",
                    payer = PayerRequest(name = "Nada Hassan", phone = "01001234567"),
                    returnUrl = "eg.bahr.app://checkout/BRL-7K4M2X9P",
                    guestPhone = "+201001234567",
                )
            val result = service(PAYMENT_STARTED, HttpStatusCode.Created).startPayment("BRL-7K4M2X9P", request)

            val sent = requests.single()
            assertEquals(HttpMethod.Post, sent.method)
            assertEquals("/api/v1/bookings/BRL-7K4M2X9P/payments", sent.url.encodedPath)
            // Never in the URL (openapi `guestPhone`): access logs must not see a phone.
            assertEquals("", sent.url.encodedQuery)
            val body = Json.parseToJsonElement((sent.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            assertEquals("vodafone_cash", body["method"]?.jsonPrimitive?.content)
            val payer = body["payer"] as JsonObject
            assertEquals("Nada Hassan", payer["name"]?.jsonPrimitive?.content)
            assertEquals("01001234567", payer["phone"]?.jsonPrimitive?.content)
            assertEquals("eg.bahr.app://checkout/BRL-7K4M2X9P", body["returnUrl"]?.jsonPrimitive?.content)
            assertEquals("+201001234567", body["guestPhone"]?.jsonPrimitive?.content)

            val started = (result as AppResult.Success).data
            assertEquals("REDIRECT", started.action.type)
            assertEquals("http://localhost:8084/fake-checkout/fake-5b0e", started.action.url)
            assertEquals("2026-10-09T22:42:30.549567+03:00", started.holdExpiresAt)
        }

    @Test
    fun `no return link and no hold phone are left out - not sent as null`() =
        runTest {
            val request = StartPaymentRequest(method = "card", payer = PayerRequest(name = "Nada Hassan", phone = "01001234567"))
            val result = service(WALLET_PENDING, HttpStatusCode.Created).startPayment("BRL-7K4M2X9P", request)

            val body =
                Json
                    .parseToJsonElement(
                        (requests.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                    ).jsonObject
            assertFalse("returnUrl" in body)
            assertFalse("guestPhone" in body)
            val started = (result as AppResult.Success).data
            assertEquals("WALLET_PENDING", started.action.type)
            assertNull(started.action.url)
        }

    @Test
    fun `a declined start is a failure carrying the contract's code`() =
        runTest {
            val request = StartPaymentRequest(method = "vodafone_cash", payer = PayerRequest(name = "Nada Hassan", phone = "01000000402"))
            val result = service(DECLINED, HttpStatusCode.PaymentRequired).startPayment("BRL-7K4M2X9P", request)

            val error = (result as AppResult.Failure).error
            assertIs<AppError.Api>(error)
            assertEquals(ApiErrorCodes.PAYMENT_DECLINED, error.code)
            assertEquals(402, error.httpStatus)
        }

    @Test
    fun `held seats carry the accepted methods - test cash where the server takes it - MV-B2`() =
        runTest {
            val held = (service(HELD_WITH_METHODS, HttpStatusCode.Created).placeHold(request) as AppResult.Success).data

            assertEquals(listOf("card", "vodafone_cash", "test_cash"), held.paymentMethods)
            // An older server sends none: the screen then offers card and Vodafone Cash.
            requests.clear()
            assertNull((service(HELD, HttpStatusCode.Created).placeHold(request) as AppResult.Success).data.paymentMethods)
        }

    @Test
    fun `a method this app does not know does not fail the decode`() =
        runTest {
            val booking = (service(BOOKING_HELD_FUTURE_METHOD).heldBooking("BRL-Y4GHX1PW", "+201000000777") as AppResult.Success).data

            assertEquals(listOf("card", "instapay"), booking.paymentMethods)
        }

    @Test
    fun `test cash starts with the contract's body and answers CONFIRMED with nothing to open`() =
        runTest {
            val request =
                StartPaymentRequest(
                    method = "test_cash",
                    payer = PayerRequest(name = "Nada Hassan", phone = "01001234567"),
                    guestPhone = "+201001234567",
                )
            val result = service(TEST_CASH_CONFIRMED, HttpStatusCode.Created).startPayment("BRL-7K4M2X9P", request)

            val body =
                Json
                    .parseToJsonElement(
                        (requests.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                    ).jsonObject
            assertEquals("test_cash", body["method"]?.jsonPrimitive?.content)
            val started = (result as AppResult.Success).data
            assertEquals("CONFIRMED", started.action.type)
            assertNull(started.action.url)
        }

    @Test
    fun `the confirmation reads the contract's Booking - paid - with its method`() =
        runTest {
            val result = service(BOOKING_CONFIRMED_TEST_CASH).bookingByRef("BRL-Y4GHX1PW")

            assertEquals("/api/v1/bookings/BRL-Y4GHX1PW", requests.single().url.encodedPath)
            // Signed in, the token proves it: no phone in the query.
            assertEquals("", requests.single().url.encodedQuery)
            val booking = (result as AppResult.Success).data
            assertEquals("CONFIRMED", booking.status)
            assertEquals("test_cash", booking.method)
            assertEquals(MoneyDto(450, "EGP"), booking.paid)
            assertEquals("Dawn on Lake Burullus", booking.trip?.title)
            assertNull(booking.holdExpiresAt)
            assertNull(booking.paymentMethods, "only while HELD")
            // MV-M5: the ticket's host row and WhatsApp button.
            assertEquals("Ashraf El Bahr", booking.host?.name)
            assertEquals("+201001234567", booking.host?.phone)
            assertEquals("https://wa.me/201001234567", booking.host?.whatsappUrl)
            assertNull(booking.dateCalledOff)
            assertNull(booking.hostPaid)
        }

    /** MV-M5: a called-off date (M6-B3) and a host's own unpaid booking (M5-B3) decode, for the ticket's header and money row. */
    @Test
    fun `the ticket reads dateCalledOff and hostPaid`() =
        runTest {
            val payload =
                BOOKING_CONFIRMED_TEST_CASH
                    .replace("\"status\":\"CONFIRMED\"", "\"status\":\"CANCELLED\",\"dateCalledOff\":true,\"hostPaid\":false")
            val booking = (service(payload).bookingByRef("BRL-Y4GHX1PW") as AppResult.Success).data
            assertEquals("CANCELLED", booking.status)
            assertEquals(true, booking.dateCalledOff)
            assertEquals(false, booking.hostPaid)
        }

    private object NoSession : SessionStore {
        override suspend fun read(): StoredSession? = null

        override suspend fun write(session: StoredSession?) = Unit
    }

    private companion object {
        const val BASE_URL = "http://localhost/api/v1/"
        const val DEVICE_ID = "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e"

        /** As served by the local api (2026-10-09) for `getBooking` on a fresh hold; the contract's `Booking`. */
        val BOOKING_HELD =
            """
            {"success":true,"data":{"ref":"BRL-Y4GHX1PW","status":"HELD","holdExpiresAt":"2026-10-09T23:20:40.39219+03:00",
            "serverNow":"2026-10-09T23:05:40.453816+03:00","trip":{"slug":"burullus-dawn","title":"Dawn on Lake Burullus",
            "cardImage":{"url":"http://localhost:9000/bahr-assets/seed/card-dawn.png","width":600,"height":600,
            "alt":"The lake at dawn","lqip":"data:image/png;base64,iVBORw0KGgo=","variants":[{"url":
            "http://localhost:9000/bahr-assets/seed/card-dawn.png?w=390&fm=webp","width":390,"format":"webp"}]}},
            "date":"2026-11-07","dayLabel":"Sat 7 Nov","departure":{"placeName":"Abdel Moneim Riad","city":"Cairo",
            "governorate":"Cairo","lat":30.0566,"lng":31.2288,"timeLocal":"05:00","arriveBy":"04:45"},"returnTime":"22:00",
            "partySize":1,"total":{"amount":450,"currency":"EGP"}}}
            """.trimIndent()

        /** The same booking after `releaseHold`: CANCELLED, no `holdExpiresAt`. */
        val BOOKING_CANCELLED =
            """
            {"success":true,"data":{"ref":"BRL-Y4GHX1PW","status":"CANCELLED","serverNow":"2026-10-09T23:05:40.496852+03:00",
            "trip":{"slug":"burullus-dawn","title":"Dawn on Lake Burullus"},"date":"2026-11-07","dayLabel":"Sat 7 Nov",
            "departure":{"placeName":"Abdel Moneim Riad","city":"Cairo","timeLocal":"05:00"},"returnTime":"22:00",
            "partySize":1,"total":{"amount":450,"currency":"EGP"}}}
            """.trimIndent()

        /**
         * [HELD] from a server that takes test cash (MV-B2): `paymentMethods`, in the server's order. A getter: [HELD] is
         * declared further down, and a companion's vals initialise in order.
         */
        val HELD_WITH_METHODS
            get() =
                HELD.replace(
                    "\"seatsRemaining\":10}",
                    "\"seatsRemaining\":10,\"paymentMethods\":[\"card\",\"vodafone_cash\",\"test_cash\"]}",
                )

        /** [BOOKING_HELD] listing a method added to the contract after this app was built. */
        val BOOKING_HELD_FUTURE_METHOD
            get() =
                BOOKING_HELD.replace("\"partySize\":1,", "\"partySize\":1,\"paymentMethods\":[\"card\",\"instapay\"],")

        /**
         * `getBooking` on a booking paid with test cash (MV-B2), the shape the confirmation reads: CONFIRMED, `paid`,
         * `method`, the host; no `holdExpiresAt`, no `paymentMethods`.
         */
        val BOOKING_CONFIRMED_TEST_CASH =
            """
            {"success":true,"data":{"ref":"BRL-Y4GHX1PW","status":"CONFIRMED","serverNow":"2026-10-09T23:06:10.112+03:00",
            "trip":{"slug":"burullus-dawn","title":"Dawn on Lake Burullus"},"date":"2026-11-07","dayLabel":"Sat 7 Nov",
            "departure":{"placeName":"Abdel Moneim Riad","city":"Cairo","timeLocal":"05:00","arriveBy":"04:45"},
            "returnTime":"22:00","returnDate":"2026-11-07","partySize":1,"total":{"amount":450,"currency":"EGP"},
            "paid":{"amount":450,"currency":"EGP"},"method":"test_cash",
            "host":{"name":"Ashraf El Bahr","phone":"+201001234567","whatsappUrl":"https://wa.me/201001234567"},
            "calendar":{"start":"2026-11-07T05:00:00+02:00","end":"2026-11-07T22:00:00+02:00","title":"Dawn on Lake Burullus",
            "location":"Abdel Moneim Riad, Cairo"}}}
            """.trimIndent()

        /** `PaymentStarted` for test cash (MV-B2): already CONFIRMED, no url. */
        val TEST_CASH_CONFIRMED =
            """
            {"success":true,"data":{"paymentId":"0199c3a0-5eed-7000-8000-00000000a003",
            "action":{"type":"CONFIRMED","url":null},"holdExpiresAt":"2026-10-09T22:42:30.549567+03:00"}}
            """.trimIndent()

        /** The contract's `PaymentStarted` as the fake provider answers it (M3-B2): a hosted checkout to open. */
        val PAYMENT_STARTED =
            """
            {"success":true,"data":{"paymentId":"0199c3a0-5eed-7000-8000-00000000a001",
            "action":{"type":"REDIRECT","url":"http://localhost:8084/fake-checkout/fake-5b0e"},
            "holdExpiresAt":"2026-10-09T22:42:30.549567+03:00"}}
            """.trimIndent()

        /** `WALLET_PENDING` with `url: null`, as the contract types it. */
        val WALLET_PENDING =
            """
            {"success":true,"data":{"paymentId":"0199c3a0-5eed-7000-8000-00000000a002",
            "action":{"type":"WALLET_PENDING","url":null},"holdExpiresAt":"2026-10-09T22:42:30.549567+03:00"}}
            """.trimIndent()

        val DECLINED =
            """
            {"success":false,"error":{"code":"PAYMENT_DECLINED","message":"The payment provider declined this payment"}}
            """.trimIndent()

        val CONFLICT =
            """
            {"success":false,"error":{"code":"CONFLICT","message":"Payment has started"}}
            """.trimIndent()

        /** As served by the local api (M2-B2) for `placeHold`; the contract's `HeldSeats`. */
        val HELD =
            """
            {"success":true,"data":{"ref":"BRL-7K4M2X9P","departureId":"0199c3a0-5eed-7000-8000-000000000601",
            "partySize":2,"pricePerPerson":{"amount":450,"currency":"EGP"},"total":{"amount":900,"currency":"EGP"},
            "holdExpiresAt":"2026-10-09T22:42:30.549567+03:00","serverNow":"2026-10-09T22:27:30.549567+03:00",
            "seatsRemaining":10}}
            """.trimIndent()

        val NO_SEATS =
            """
            {"success":false,"error":{"code":"NO_SEATS_AVAILABLE","message":"There are not enough seats left on this departure"}}
            """.trimIndent()

        /** A trimmed `TripDetail`: the screen ignores the rest of the shape. */
        val TRIP =
            """
            {"success":true,"data":{"slug":"burullus-dawn","title":"Dawn on Lake Burullus","durationLabel":"05:00 → 22:00",
            "price":{"amount":450,"currency":"EGP"},"host":{"name":"Ashraf El Bahr","verified":true},
            "policy":{"freeCancellationHours":72,"maxPartySize":6}}}
            """.trimIndent()

        val DATES =
            """
            {"success":true,"data":[
            {"id":"0199c3a0-5eed-7000-8000-000000000601","date":"2026-10-17","dayLabel":"Sat 17 Oct","departTime":"05:00",
            "returnTime":"22:00","seatsRemaining":6,"capacity":18,"soldOut":false,"bookable":true,"price":{"amount":450,"currency":"EGP"}},
            {"id":"0199c3a0-5eed-7000-8000-000000000603","date":"2026-10-31","dayLabel":"Sat 31 Oct","departTime":"05:00",
            "returnTime":"22:00","seatsRemaining":0,"capacity":18,"soldOut":true,"bookable":false,"unavailableReason":"SOLD_OUT",
            "price":{"amount":450,"currency":"EGP"}}]}
            """.trimIndent()
    }
}
