package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.trips.model.ReviewPageDto
import eg.bahr.feature.trips.model.ReviewSort
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

/** `GET /api/v1/reviews` (`listReviews`, M4-M11) through the app's real client on a MockEngine. */
class ReviewsApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String = TRIP_PAGE,
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
    fun `a trip's reviews - the trip slug with the sort and the page - no host and no stars`() =
        runTest {
            service(language = "ar").reviews(trip = "burullus-dawn", host = null, sort = ReviewSort.NEWEST, stars = null, page = 1)

            val request = requests.single()
            assertEquals("/api/v1/reviews", request.url.encodedPath)
            val query = request.url.parameters
            assertEquals("burullus-dawn", query["trip"])
            assertFalse(query.contains("host"))
            assertFalse(query.contains("stars"))
            assertEquals("newest", query["sort"])
            assertEquals("1", query["page"])
            assertEquals(TripApiService.PAGE_SIZE.toString(), query["size"])
            assertEquals("ar", request.headers[HttpHeaders.AcceptLanguage])
        }

    @Test
    fun `a host's reviews narrowed to five stars - highest first`() =
        runTest {
            service().reviews(trip = null, host = "ashraf-el-bahr", sort = ReviewSort.HIGHEST, stars = 5)

            val query = requests.single().url.parameters
            assertEquals("ashraf-el-bahr", query["host"])
            assertFalse(query.contains("trip"))
            assertEquals("highest", query["sort"])
            assertEquals("5", query["stars"])
            assertEquals("0", query["page"])
        }

    @Test
    fun `decodes a page - with a rating-only review - and the scope's summary`() =
        runTest {
            val page = success(service().reviews(trip = "burullus-dawn", host = null, sort = ReviewSort.NEWEST, stars = null))

            assertEquals(listOf("Tarek N.", "Heba R.", "Nada H."), page.items.map { it.author })
            assertEquals(listOf(4, 5, 5), page.items.map { it.rating })
            // Heba's is a rating only: stars, no text.
            assertNull(page.items[1].body)
            assertEquals(LocalDate(2026, 9, 28), page.items[1].dateISO)
            assertEquals(5L, page.totalItems)
            assertEquals(2, page.totalPages)
            assertEquals(4.8, page.rating.average)
            assertEquals(5, page.rating.count)
            assertEquals(listOf(4, 1, 0, 0, 0), page.rating.stars.map { it.count })
        }

    @Test
    fun `a review from an older server - no rating - reads with none`() =
        runTest {
            val body =
                """
                {"success":true,"data":{"items":[{"author":"Old","dateISO":"2026-01-01","body":"Fine."}],
                "page":0,"size":20,"totalItems":1,"totalPages":1,"rating":{"count":1,"distribution":[]}}}
                """.trimIndent()

            val page = success(service(body).reviews(trip = "t", host = null, sort = ReviewSort.NEWEST, stars = null))

            assertNull(page.items.single().rating)
        }

    @Test
    fun `an unpublished trip or a hidden host is NOT_FOUND by its code`() =
        runTest {
            val result =
                service(NOT_FOUND, status = HttpStatusCode.NotFound)
                    .reviews(trip = "nope", host = null, sort = ReviewSort.NEWEST, stars = null)

            val error = assertIs<AppError.Api>(assertIs<AppResult.Failure>(result).error)
            assertEquals("NOT_FOUND", error.code)
        }

    private fun success(result: AppResult<ReviewPageDto>): ReviewPageDto {
        assertIs<AppResult.Success<ReviewPageDto>>(result)
        return result.data
    }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"

        /** The local API's answer (M4-B8 seed) to `GET /reviews?trip=burullus-dawn&size=3`, in English. */
        const val TRIP_PAGE =
            """{"success":true,"data":{"items":[{"author":"Tarek N.","dateISO":"2026-10-03","body":"The boat and the birds were wonderful, but the bus was half an hour late in the morning.","partyLabel":"With my wife","tone":"primary","rating":4},{"author":"Heba R.","dateISO":"2026-09-28","tone":"success","rating":5},{"author":"Nada H.","dateISO":"2026-09-21","body":"At 09:00 the lake is completely flat and silent, and Ashraf cuts the motor so you hear the birds.","partyLabel":"Went with 3 friends","tone":"quaternary","rating":5}],"page":0,"size":3,"totalItems":5,"totalPages":2,"rating":{"average":4.8,"count":5,"distribution":[{"stars":5,"count":4},{"stars":4,"count":1},{"stars":3,"count":0},{"stars":2,"count":0},{"stars":1,"count":0}]}}}"""

        const val NOT_FOUND = """{"success":false,"error":{"code":"NOT_FOUND","message":"Trip nope not found"}}"""
    }
}
