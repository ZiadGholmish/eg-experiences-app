package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.trips.model.FacetDto
import eg.bahr.feature.trips.model.TripCardPageDto
import eg.bahr.feature.trips.model.TripPageDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M4-M1b on the wire: `GET /trips` with `category` / `filter` and its facets (M4-B1), and
 * `GET /home/sections/{id}/trips` (`listHomeSectionTrips`, M4-B1b), through the app's real client on
 * a MockEngine. Bodies follow openapi `TripPage`, `Facet` and `TripCardPage`.
 */
class TripFacetsApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): TripApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        return TripApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "en" })
    }

    @Test
    fun `a category and a filter go in the query`() =
        runTest {
            service(FILTERED).listTrips(page = 1, category = "on_the_boat", filter = "weekend")

            val url = requests.single().url
            assertEquals("/api/v1/trips", url.encodedPath)
            assertEquals("on_the_boat", url.parameters["category"])
            assertEquals("weekend", url.parameters["filter"])
            assertEquals("1", url.parameters["page"])
            assertEquals("${TripApiService.PAGE_SIZE}", url.parameters["size"])
        }

    @Test
    fun `a cleared category or filter is left out - never sent empty`() =
        runTest {
            service(FILTERED).listTrips()

            val parameters = requests.single().url.parameters
            assertFalse("category" in parameters.names())
            assertFalse("filter" in parameters.names())
        }

    @Test
    fun `facets decode in the served order with type - tone - count and selected`() =
        runTest {
            val page = success(service(FILTERED).listTrips(category = "on_the_boat"))

            assertEquals(
                listOf("filter:all", "filter:weekend", "filter:under_400", "category:on_the_boat"),
                page.facets.map { "${it.type}:${it.key}" },
            )
            assertEquals(
                FacetDto(
                    type = "category",
                    key = "on_the_boat",
                    label = "On the boat",
                    icon = "sailing",
                    tone = "primary",
                    count = 3,
                    selected = true,
                ),
                page.facets.last(),
            )
            assertEquals(0, page.facets[2].count, "a zero-count chip is served, to be drawn dimmed")
            assertNull(page.facets.first().tone, "filter chips carry no tone")
            assertTrue(page.facets.first().selected, "no filter given: `all` is the selected one")
            assertEquals(3L, page.totalItems)
        }

    @Test
    fun `a refused category key is VALIDATION_FAILED`() =
        runTest {
            val result = service(VALIDATION_FAILED, HttpStatusCode.BadRequest).listTrips(category = "gone")

            assertIs<AppResult.Failure>(result)
            val error = assertIs<AppError.Api>(result.error)
            assertEquals(ApiErrorCodes.VALIDATION_FAILED, error.code)
        }

    @Test
    fun `see all reads the section's own list a page at a time`() =
        runTest {
            val result = service(SECTION_PAGE).sectionTrips(sectionId = SECTION_ID, page = 2)

            val url = requests.single().url
            assertEquals("/api/v1/home/sections/$SECTION_ID/trips", url.encodedPath)
            assertEquals("2", url.parameters["page"])
            assertEquals("${TripApiService.PAGE_SIZE}", url.parameters["size"])
            assertIs<AppResult.Success<TripCardPageDto>>(result)
            assertEquals(listOf("burullus-dawn"), result.data.items.map { it.slug })
            assertEquals(41L, result.data.totalItems)
            assertEquals(3, result.data.totalPages)
        }

    @Test
    fun `a section that is gone is NOT_FOUND`() =
        runTest {
            val result = service(NOT_FOUND, HttpStatusCode.NotFound).sectionTrips(sectionId = SECTION_ID, page = 0)

            assertIs<AppResult.Failure>(result)
            assertEquals(ApiErrorCodes.NOT_FOUND, assertIs<AppError.Api>(result.error).code)
        }

    private fun success(result: AppResult<TripPageDto>): TripPageDto {
        assertIs<AppResult.Success<TripPageDto>>(result)
        return result.data
    }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
        const val SECTION_ID = "0199c3a0-5eed-7000-8000-000000000702"

        const val CARD = """{"slug":"burullus-dawn","title":"Dawn on Lake Burullus","durationLabel":"05:00 → 22:00",
            "price":{"amount":450,"currency":"EGP"},"categories":["on_the_boat"]}"""

        /** `GET /trips?category=on_the_boat`, trimmed to three filter chips and the active category chip. */
        const val FILTERED = """{"success":true,"data":{"items":[$CARD],"page":0,"size":20,"totalItems":3,"totalPages":1,
            "facets":[
              {"type":"filter","key":"all","label":"All trips","icon":"apps","count":3,"selected":true},
              {"type":"filter","key":"weekend","label":"Weekend","icon":"calendar_month","count":2,"selected":false},
              {"type":"filter","key":"under_400","label":"Under 400","icon":"sell","count":0,"selected":false},
              {"type":"category","key":"on_the_boat","label":"On the boat","icon":"sailing","tone":"primary","count":3,"selected":true}
            ]}}"""

        const val SECTION_PAGE = """{"success":true,"data":{"items":[$CARD],"page":2,"size":20,"totalItems":41,"totalPages":3}}"""

        const val VALIDATION_FAILED =
            """{"success":false,"data":null,"error":{"code":"VALIDATION_FAILED","message":"Unknown category: gone"}}"""

        const val NOT_FOUND = """{"success":false,"data":null,"error":{"code":"NOT_FOUND","message":"No such section"}}"""
    }
}
