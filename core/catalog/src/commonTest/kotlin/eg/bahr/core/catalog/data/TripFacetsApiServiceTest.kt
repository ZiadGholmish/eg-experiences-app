package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.TripPageDto
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.apiHttpClient
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
 * M4-M1b on the wire: `GET /trips` with `category` / `filter` and its facets (M4-B1), through the app's
 * real client on a MockEngine. Bodies follow openapi `TripPage` and `Facet`. A row's "See all"
 * (`listHomeSectionTrips`) is tested in feature:trips, which owns it (M4-M1c split this file).
 */
class TripFacetsApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): TripListApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        return TripListApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "en" })
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
            assertEquals("${TripListApiService.PAGE_SIZE}", url.parameters["size"])
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

    private fun success(result: AppResult<TripPageDto>): TripPageDto {
        assertIs<AppResult.Success<TripPageDto>>(result)
        return result.data
    }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"

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

        const val VALIDATION_FAILED =
            """{"success":false,"data":null,"error":{"code":"VALIDATION_FAILED","message":"Unknown category: gone"}}"""
    }
}
