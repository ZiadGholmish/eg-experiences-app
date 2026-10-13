package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.trips.model.HostKind
import eg.bahr.feature.trips.model.HostProfileDto
import eg.bahr.feature.trips.model.StarCountDto
import eg.bahr.feature.trips.model.stars
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `GET /api/v1/hosts/{slug}` (`getHost`, M4-M10) through the app's real client on a MockEngine. */
class HostApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        language: String = "en",
        status: HttpStatusCode = HttpStatusCode.OK,
    ): TripApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        return TripApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { language })
    }

    @Test
    fun `asks for the host by slug in the stored language`() =
        runTest {
            service(HostPayloads.redSeaDaysArabic, language = "ar").hostBySlug("red-sea-days")

            val request = requests.single()
            assertEquals("/api/v1/hosts/red-sea-days", request.url.encodedPath)
            assertEquals("ar", request.headers[HttpHeaders.AcceptLanguage])
        }

    @Test
    fun `decodes a host with reviews`() =
        runTest {
            val result = service(HostPayloads.ashrafEnglish).hostBySlug("ashraf-el-bahr")

            val host = success(result)
            assertEquals("Ashraf El Bahr", host.name)
            assertFalse(HostKind.isCompany(host.kind))
            assertTrue(host.verified)
            assertEquals(LocalDate(2024, 9, 14), host.memberSince)
            assertEquals(11, host.tripsRun)
            assertEquals(4.7, host.rating.average)
            assertEquals(6, host.rating.count)
            assertEquals(listOf(4, 2, 0, 0, 0), host.rating.stars.map { it.count })
            assertEquals(listOf("Mahmoud A.", "Salma K."), host.reviews.map { it.author })
            assertEquals(LocalDate(2026, 9, 21), host.reviews.first().dateISO)
        }

    @Test
    fun `decodes a company with no reviews yet - no average and five zero bars`() =
        runTest {
            val result = service(HostPayloads.redSeaDaysArabic, language = "ar").hostBySlug("red-sea-days")

            val host = success(result)
            // M4-B10: the name is served in the request's language, and shown as served.
            assertEquals("ريد سي دايز", host.name)
            assertTrue(HostKind.isCompany(host.kind))
            assertNull(host.rating.average)
            assertEquals(0, host.rating.count)
            assertEquals(listOf(5, 4, 3, 2, 1), host.rating.stars.map { it.stars })
            assertEquals(emptyList(), host.reviews)
        }

    @Test
    fun `an unknown or unapproved host is NOT_FOUND by its code`() =
        runTest {
            val result = service(HostPayloads.NOT_FOUND, status = HttpStatusCode.NotFound).hostBySlug("pending-host")

            val error = assertIs<AppError.Api>(assertIs<AppResult.Failure>(result).error)
            assertEquals("NOT_FOUND", error.code)
        }

    @Test
    fun `a kind this build does not know reads as a person - and stray or missing star buckets do not move the bars`() =
        runTest {
            val body =
                """
                {"success":true,"data":{"slug":"x","kind":"cooperative","name":"X","verified":false,"tripsRun":1,
                "rating":{"average":4.0,"count":3,"distribution":[{"stars":1,"count":1},{"stars":5,"count":2},{"stars":9,"count":7}]},
                "reviews":[]}}
                """.trimIndent()

            val result = service(body).hostBySlug("x")

            val host = success(result)
            assertFalse(HostKind.isCompany(host.kind))
            assertEquals(
                listOf(StarCountDto(5, 2), StarCountDto(4, 0), StarCountDto(3, 0), StarCountDto(2, 0), StarCountDto(1, 1)),
                host.rating.stars,
            )
        }

    private fun success(result: AppResult<HostProfileDto>): HostProfileDto {
        assertIs<AppResult.Success<HostProfileDto>>(result)
        return result.data
    }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
    }
}
