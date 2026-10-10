package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.trips.model.TripPageDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * M4-M3 on the wire: `GET /trips?q=` (`listTrips`, M4-B2) through the app's real client on a
 * MockEngine. The text goes as typed, URL-encoded, next to the other parameters; the answer is the
 * ordinary `TripPage`.
 */
class TripSearchApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(): TripApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = PAGE,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        return TripApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "ar" })
    }

    @Test
    fun `arabic text goes exactly as typed - encoded on the wire - decoded the same`() =
        runTest {
            val typed = "فُلوكة  البرلس"

            val result = service().listTrips(q = typed, filter = "weekend")

            val url = requests.single().url
            assertEquals(typed, url.parameters["q"], "no folding, trimming or collapsing on the client")
            assertEquals("weekend", url.parameters["filter"])
            assertEquals("0", url.parameters["page"])
            assertFalse(url.encodedQuery.contains("ف"), "the Arabic is percent-encoded on the wire")
            assertIs<AppResult.Success<TripPageDto>>(result)
            assertEquals(listOf("burullus-dawn"), result.data.items.map { it.slug })
        }

    @Test
    fun `characters with a meaning in a url are sent literally`() =
        runTest {
            service().listTrips(q = "50% & more")

            assertEquals("50% & more", requests.single().url.parameters["q"])
        }

    @Test
    fun `no search is no q parameter at all`() =
        runTest {
            service().listTrips()

            val sent = requests.single().url.parameters
            assertTrue("q" !in sent.names())
        }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"

        /** openapi `TripPage`, best match first, with the facets counting only matches. */
        const val PAGE = """{"success":true,"data":{"items":[
            {"slug":"burullus-dawn","title":"الفجر على بحيرة البرلس","durationLabel":"05:00 → 22:00",
             "price":{"amount":450,"currency":"EGP"},"categories":["on_the_boat"]}],
            "page":0,"size":20,"totalItems":1,"totalPages":1,
            "facets":[{"type":"filter","key":"all","label":"كل الرحلات","icon":"apps","count":1,"selected":false},
              {"type":"filter","key":"weekend","label":"الويك إند","icon":"calendar_month","count":1,"selected":true}]}}"""
    }
}
