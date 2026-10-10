package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.trips.model.TripPageDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * `GET /api/v1/trips` through the app's real client (contract JSON settings, base URL,
 * `Accept-Language`) on a Ktor MockEngine, answered with the seeded trips in the contract's shape.
 */
class TripApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        language: String = "en",
    ): TripApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respondJson(body)
            }
        return TripApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { language })
    }

    @Test
    fun `asks for the first page of trips with the stored language`() =
        runTest {
            service(TripListPayloads.arabic, language = "ar").listTrips()

            val request = requests.single()
            assertEquals("/api/v1/trips", request.url.encodedPath)
            assertEquals("0", request.url.parameters["page"])
            assertEquals("${TripApiService.PAGE_SIZE}", request.url.parameters["size"])
            assertEquals("ar", request.headers[HttpHeaders.AcceptLanguage])
        }

    @Test
    fun `decodes the four seeded trips in English`() =
        runTest {
            val page = success(service(TripListPayloads.english).listTrips())

            assertEquals(
                listOf("burullus-dawn", "reed-channels-kayak", "burullus-murals", "boughaz-fish-market"),
                page.items.map { it.slug },
            )
            assertEquals(listOf(450L, 520L, 220L, 380L), page.items.map { it.price.amount })
            assertEquals(1, page.totalPages)

            val dawn = page.items.first()
            assertEquals("Dawn on Lake Burullus", dawn.title)
            assertEquals("05:00 → 22:00", dawn.durationLabel)
            assertEquals(MoneyDto(amount = 450, currencyCode = "EGP"), dawn.price)
            assertEquals(listOf("on_the_boat", "birds", "food"), dawn.categories)
            assertEquals("primary", dawn.badge?.tone)
            assertEquals(37, dawn.rating?.count)
            assertEquals("http://localhost:9000/bahr-assets/seed/card-dawn.png", dawn.cardImage?.url)
            assertEquals(true, dawn.cardImage?.lqip?.startsWith("data:image/png;base64,"))
            assertEquals(LocalDate(2026, 10, 10), dawn.nextDeparture?.date)
            assertEquals(6, dawn.nextDeparture?.seatsRemaining)
            assertEquals(false, dawn.nextDeparture?.soldOut)
        }

    @Test
    fun `decodes the Arabic page and tolerates the fields the backend leaves out`() =
        runTest {
            val page = success(service(TripListPayloads.arabic, language = "ar").listTrips())

            assertEquals("الفجر على بحيرة البرلس", page.items.first().title)
            val kayak = page.items[1]
            assertEquals("موسم الفلامنجو", kayak.badge?.label)
            assertEquals("secondary", kayak.badge?.tone)
            assertEquals("http://localhost:9000/bahr-assets/seed/joy-birds.png", kayak.heroImage?.url)
            assertEquals(560, kayak.heroImage?.height)
            assertEquals("طيور بين البوص", kayak.heroImage?.alt)
            // Omitted by the backend: no rating yet, and facets are not served before M4.
            assertNull(kayak.rating)
            assertEquals(emptyList(), page.facets)
        }

    @Test
    fun `a field or tone this build does not know does not break the page`() =
        runTest {
            val body =
                """
                {"success":true,"data":{"items":[{"slug":"new-trip","title":"New","durationLabel":"",
                "price":{"amount":300,"currency":"EGP"},"badge":{"label":"New","tone":"ultraviolet"},
                "addedLater":{"anything":1}}],"page":0,"size":20,"totalItems":1,"totalPages":1}}
                """.trimIndent()

            val trip = success(service(body).listTrips()).items.single()

            assertEquals("ultraviolet", trip.badge?.tone)
            assertNull(trip.nextDeparture)
            assertNull(trip.cardImage)
            assertEquals(emptyList(), trip.categories)
        }

    @Test
    fun `a card missing a required field is a serialization error and never a half-filled card`() =
        runTest {
            val body = """{"success":true,"data":{"items":[{"slug":"x","title":"X","durationLabel":""}]}}"""

            val result = service(body).listTrips()

            assertIs<AppResult.Failure>(result)
            assertIs<AppError.Serialization>(result.error)
        }

    private fun success(result: AppResult<TripPageDto>): TripPageDto {
        assertIs<AppResult.Success<TripPageDto>>(result)
        return result.data
    }

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData =
        respond(
            content = body,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
    }
}
