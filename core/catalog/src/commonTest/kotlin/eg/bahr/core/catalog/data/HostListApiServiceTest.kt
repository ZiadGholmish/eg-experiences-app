package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.HostKindKey
import eg.bahr.core.catalog.model.HostListQuery
import eg.bahr.core.catalog.model.HostPageDto
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
 * MV-M1 on the wire: `GET /hosts` (`listHosts`, MV-B1) through the app's real client on a MockEngine. Bodies follow
 * openapi `HostPage`, `HostCard` and `GovernorateChip`, with the contract's example values.
 */
class HostListApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HostListApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        return HostListApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "ar" })
    }

    @Test
    fun `every choice goes in the query - search text as typed`() =
        runTest {
            service(PAGE).listHosts(
                HostListQuery(q = "هدى عيسى", sort = "most_trips", category = "birds", governorate = "kafr-el-sheikh", kind = "company"),
                page = 1,
                size = HOST_PAGE_SIZE,
            )

            val url = requests.single().url
            assertEquals("/api/v1/hosts", url.encodedPath)
            assertEquals("هدى عيسى", url.parameters["q"])
            assertEquals("most_trips", url.parameters["sort"])
            assertEquals("birds", url.parameters["category"])
            assertEquals("kafr-el-sheikh", url.parameters["governorate"])
            assertEquals("company", url.parameters["kind"])
            assertEquals("1", url.parameters["page"])
            assertEquals("$HOST_PAGE_SIZE", url.parameters["size"])
            assertEquals("ar", requests.single().headers[HttpHeaders.AcceptLanguage])
        }

    @Test
    fun `a choice not made is left out - never sent empty`() =
        runTest {
            service(PAGE).listHosts(HostListQuery(), page = 0, size = SEARCH_HOSTS_SIZE)

            val parameters = requests.single().url.parameters
            listOf("q", "sort", "category", "governorate", "kind").forEach { assertFalse(it in parameters.names(), it) }
            assertEquals("$SEARCH_HOSTS_SIZE", parameters["size"])
        }

    @Test
    fun `a page decodes cards and region chips - absent rating and avatar are null`() =
        runTest {
            val page = success(service(PAGE).listHosts(HostListQuery(), 0, HOST_PAGE_SIZE))

            assertEquals(2, page.items.size)
            val redSea = page.items[0]
            assertEquals("red-sea-days", redSea.slug)
            assertTrue(HostKindKey.isCompany(redSea.kind))
            assertEquals(4.5, redSea.rating?.value)
            assertEquals(12, redSea.rating?.count)
            assertEquals(3, redSea.publishedTrips)
            assertEquals(listOf("red-sea" to "البحر الأحمر"), redSea.governorates.map { it.key to it.label })
            val hoda = page.items[1]
            assertNull(hoda.rating)
            assertNull(hoda.avatar)
            assertFalse(HostKindKey.isCompany(hoda.kind))

            assertEquals(listOf("kafr-el-sheikh", "red-sea"), page.governorates.map { it.key })
            assertEquals(0, page.governorates[1].count)
            assertTrue(page.governorates[0].selected)
            assertEquals(7, page.totalItems)
        }

    @Test
    fun `a kind this build does not know reads as a person`() =
        runTest {
            val page = success(service(PAGE.replace("\"person\"", "\"cooperative\"")).listHosts(HostListQuery(), 0, HOST_PAGE_SIZE))
            assertFalse(HostKindKey.isCompany(page.items[1].kind))
        }

    @Test
    fun `an unknown key is VALIDATION_FAILED - branched on the code`() =
        runTest {
            val result =
                service(
                    VALIDATION_FAILED,
                    HttpStatusCode.BadRequest,
                ).listHosts(HostListQuery(governorate = "x"), 0, HOST_PAGE_SIZE)

            val failure = assertIs<AppResult.Failure>(result)
            val error = assertIs<AppError.Api>(failure.error)
            assertEquals(ApiErrorCodes.VALIDATION_FAILED, error.code)
        }

    private fun success(result: AppResult<HostPageDto>): HostPageDto = assertIs<AppResult.Success<HostPageDto>>(result).data

    private companion object {
        const val BASE_URL = "https://api.bahr.test/api/v1/"

        val PAGE =
            """
            {"success":true,"data":{
              "items":[
                {"slug":"red-sea-days","kind":"company","name":"ريد سي دايز","verified":true,
                 "rating":{"value":4.5,"count":12},"publishedTrips":3,
                 "governorates":[{"key":"red-sea","label":"البحر الأحمر"}]},
                {"slug":"hoda-eissa","kind":"person","name":"هدى عيسى","verified":false,"publishedTrips":1,
                 "governorates":[{"key":"kafr-el-sheikh","label":"كفر الشيخ"}]}
              ],
              "page":0,"size":20,"totalItems":7,"totalPages":1,
              "governorates":[
                {"key":"kafr-el-sheikh","label":"كفر الشيخ","count":4,"selected":true},
                {"key":"red-sea","label":"البحر الأحمر","count":0,"selected":false}
              ]
            },"error":null}
            """.trimIndent()

        val VALIDATION_FAILED =
            """{"success":false,"data":null,"error":{"code":"VALIDATION_FAILED","message":"Unknown governorate"}}"""
    }
}
