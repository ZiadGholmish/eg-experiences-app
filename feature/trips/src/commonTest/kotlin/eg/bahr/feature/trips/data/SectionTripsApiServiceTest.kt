package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.trips.model.TripCardPageDto
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
import kotlin.test.assertIs

/**
 * M4-M1b on the wire: `GET /home/sections/{id}/trips` (`listHomeSectionTrips`, M4-B1b), through the
 * app's real client on a MockEngine. Bodies follow openapi `TripCardPage`. `GET /trips` with
 * `category` / `filter` and its facets is tested in core:catalog, which owns it (M4-M1c split this file).
 */
class SectionTripsApiServiceTest {
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

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
        const val SECTION_ID = "0199c3a0-5eed-7000-8000-000000000702"

        const val CARD = """{"slug":"burullus-dawn","title":"Dawn on Lake Burullus","durationLabel":"05:00 → 22:00",
            "price":{"amount":450,"currency":"EGP"},"categories":["on_the_boat"]}"""

        const val SECTION_PAGE = """{"success":true,"data":{"items":[$CARD],"page":2,"size":20,"totalItems":41,"totalPages":3}}"""

        const val NOT_FOUND = """{"success":false,"data":null,"error":{"code":"NOT_FOUND","message":"No such section"}}"""
    }
}
