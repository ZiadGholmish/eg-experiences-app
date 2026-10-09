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

    private companion object {
        const val BASE_URL = "http://localhost/api/v1/"

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
