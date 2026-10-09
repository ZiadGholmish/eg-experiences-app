package eg.bahr.feature.booking.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.booking.model.GuestRequest
import eg.bahr.feature.booking.model.PlaceHoldRequest
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

    private companion object {
        const val BASE_URL = "http://localhost/api/v1/"

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
