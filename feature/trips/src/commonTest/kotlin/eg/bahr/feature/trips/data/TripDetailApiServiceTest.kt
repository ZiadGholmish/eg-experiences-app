package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.network.apiHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/v1/trips/{slug}` and `…/departures` through the app's real client on a MockEngine,
 * answered with the seeded `burullus-dawn` in the contract's `TripDetail` / `Departure` shape.
 */
class TripDetailApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        language: String = "en",
    ): TripApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        return TripApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { language })
    }

    private fun <T> success(result: AppResult<T>): T {
        assertIs<AppResult.Success<T>>(result)
        return result.data
    }

    @Test
    fun `asks for the trip and its dates by slug in the stored language`() =
        runTest {
            service(TripDetailPayloads.arabic, language = "ar").tripBySlug("burullus-dawn")
            service(TripDetailPayloads.arabicDepartures, language = "ar").departuresFor("burullus-dawn")

            assertEquals(
                listOf("/api/v1/trips/burullus-dawn", "/api/v1/trips/burullus-dawn/departures"),
                requests.map { it.url.encodedPath },
            )
            assertTrue(requests.all { it.headers[HttpHeaders.AcceptLanguage] == "ar" })
        }

    @Test
    fun `decodes the seeded trip page`() =
        runTest {
            val trip = success(service(TripDetailPayloads.english).tripBySlug("burullus-dawn"))

            assertEquals("Dawn on Lake Burullus", trip.title)
            assertEquals("Lake Burullus · Kafr El Sheikh", trip.subtitle)
            assertEquals(MoneyDto(amount = 450, currencyCode = "EGP"), trip.price)
            assertEquals(1020, trip.durationMinutes)
            assertEquals("04:45", trip.departure?.arriveBy)
            assertEquals("Abdel Moneim Riad", trip.departure?.placeName)
            assertEquals(150, trip.destination?.distanceKm)
            assertEquals(6, trip.included.size)
            assertEquals(5, trip.excluded.size)

            assertEquals(10, trip.itinerary.size)
            val lunch = trip.itinerary[4]
            assertEquals("12:15", lunch.time)
            assertEquals("meal", lunch.kind)
            assertEquals("restaurant", lunch.icon)
            assertTrue(lunch.approximate)
            assertEquals(false, trip.itinerary.first().approximate)

            val host = trip.host!!
            assertEquals("Ashraf El Bahr", host.name)
            assertEquals(11, host.tripsRun)
            assertTrue(host.verified)
            assertEquals(240, host.avatar?.width)
            assertEquals(listOf("bring", "best_time", "look_out", "know"), trip.tips.map { it.key })

            assertEquals(4.8, trip.reviews?.average)
            assertEquals(37, trip.reviews?.count)
            assertEquals(
                LocalDate(2026, 9, 21),
                trip.reviews
                    ?.items
                    ?.first()
                    ?.dateISO,
            )
            assertEquals(
                "quaternary",
                trip.reviews
                    ?.items
                    ?.first()
                    ?.tone,
            )

            assertEquals(72, trip.policy?.freeCancellationHours)
            assertEquals(6, trip.gallery.size)
            assertEquals(
                listOf("avif", "webp", "jpeg"),
                trip.gallery
                    .first()
                    .variants
                    .map { it.format },
            )
            assertEquals(emptyList(), trip.host.avatar?.variants)
            // Declared by the contract, not served yet.
            assertNull(trip.shareUrl)
            assertEquals(4, trip.dates.size)
        }

    @Test
    fun `decodes four Saturdays with one sold out`() =
        runTest {
            val dates = success(service(TripDetailPayloads.englishDepartures).departuresFor("burullus-dawn"))

            assertEquals(listOf(6, 2, 0, 11), dates.map { it.seatsRemaining })
            assertEquals(listOf(true, true, false, true), dates.map { it.bookable })
            val full = dates[2]
            assertTrue(full.soldOut)
            assertEquals(LocalDate(2026, 10, 24), full.date)
            assertEquals("0199c3a0-5eed-7000-8000-000000000603", full.id)
            assertEquals("Sat 24 Oct", full.dayLabel)
            assertEquals(18, full.capacity)
            assertEquals("05:00", full.departTime)
        }

    @Test
    fun `the Arabic page decodes with the Arabic copy`() =
        runTest {
            val trip = success(service(TripDetailPayloads.arabic, language = "ar").tripBySlug("burullus-dawn"))
            assertEquals("الفجر على بحيرة البرلس", trip.title)
            assertEquals("إيه اللي تجيبه معاك", trip.tips.first().title)
            val dates = success(service(TripDetailPayloads.arabicDepartures, language = "ar").departuresFor("burullus-dawn"))
            assertEquals("السبت 10 أكتوبر", dates.first().dayLabel)
        }

    @Test
    fun `a departure without its required id is a serialization error`() =
        runTest {
            val body =
                """
                {"success":true,"data":[{"date":"2026-10-10","seatsRemaining":1,"capacity":18,
                "soldOut":false,"bookable":true,"price":{"amount":450,"currency":"EGP"}}]}
                """.trimIndent()

            val result = service(body).departuresFor("burullus-dawn")

            assertIs<AppResult.Failure>(result)
            assertIs<AppError.Serialization>(result.error)
        }

    @Test
    fun `a minimal trip with only the card's required fields still decodes`() =
        runTest {
            val body =
                """
                {"success":true,"data":{"slug":"x","title":"X","durationLabel":"",
                "price":{"amount":1,"currency":"EGP"},"itinerary":[{"time":"05:00","kind":"teleport"}]}}
                """.trimIndent()

            val trip = success(service(body).tripBySlug("x"))

            assertEquals("teleport", trip.itinerary.single().kind)
            assertNull(trip.host)
            assertEquals(emptyList(), trip.dates)
        }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
    }
}
