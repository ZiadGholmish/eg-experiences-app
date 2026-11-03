package eg.bahr.feature.reviews.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.reviews.model.MyReviewDto
import eg.bahr.feature.reviews.model.ReviewDraftDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `POST /api/v1/me/bookings/{ref}/review` (`submitReview`, M3-B7) through the app's real client on a MockEngine. */
class WriteReviewApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()

    private fun service(
        body: String = CREATED,
        status: HttpStatusCode = HttpStatusCode.Created,
    ): WriteReviewApiService {
        val engine =
            MockEngine { request ->
                requests += request
                bodies += request.body.toByteArray().decodeToString()
                respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        return WriteReviewApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "ar" })
    }

    @Test
    fun `posts a multipart form to the booking's review - not the client's default JSON`() =
        runTest {
            service().submit("BHR-7Q2K", ReviewDraftDto(rating = 5, text = "Lovely"), photos = emptyList())

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/me/bookings/BHR-7Q2K/review", request.url.encodedPath)
            assertEquals("ar", request.headers[HttpHeaders.AcceptLanguage])
            val type = assertNotNull(request.body.contentType)
            assertTrue(type.match(ContentType.MultiPart.FormData), "content type was $type")
            assertTrue(type.parameter("boundary") != null, "no boundary in $type")
        }

    @Test
    fun `the review part is JSON with its own content type - the photos are named JPEG files`() =
        runTest {
            val jpegA = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 'A'.code.toByte())
            val jpegB = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 'B'.code.toByte())

            service().submit("BHR-7Q2K", ReviewDraftDto(rating = 4, text = "Good boat"), photos = listOf(jpegA, jpegB))

            val body = bodies.single()
            val parts = body.split("Content-Disposition:").drop(1)
            assertEquals(3, parts.size, body)
            val review = parts[0]
            assertTrue("name=review" in review, review)
            assertTrue("Content-Type: application/json" in review, review)
            assertTrue("""{"rating":4,"text":"Good boat"}""" in review, review)
            parts.drop(1).forEachIndexed { index, photo ->
                assertTrue("name=photos" in photo, photo)
                assertTrue("filename=\"photo-${index + 1}.jpg\"" in photo, photo)
                assertTrue("Content-Type: image/jpeg" in photo, photo)
            }
        }

    @Test
    fun `a rating only - no text is left out of the review part`() =
        runTest {
            service().submit("BHR-7Q2K", ReviewDraftDto(rating = 3, text = null), photos = emptyList())

            val body = bodies.single()
            assertTrue("""{"rating":3}""" in body, body)
            assertFalse("text" in body.substringAfter("name=review"), body)
        }

    @Test
    fun `decodes the pending review the server stored`() =
        runTest {
            val result = service().submit("BHR-7Q2K", ReviewDraftDto(rating = 5), photos = emptyList())

            val review = assertIs<AppResult.Success<MyReviewDto>>(result).data
            assertEquals("pending", review.status)
            assertEquals(5, review.rating)
            assertEquals(1, review.photos.size)
            assertFalse(review.canEdit)
        }

    @Test
    fun `a second review of the booking is CONFLICT by its code`() =
        runTest {
            val result = service(CONFLICT, HttpStatusCode.Conflict).submit("BHR-7Q2K", ReviewDraftDto(rating = 5), photos = emptyList())

            val error = assertIs<AppError.Api>(assertIs<AppResult.Failure>(result).error)
            assertEquals("CONFLICT", error.code)
            assertEquals(409, error.httpStatus)
        }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"

        /** openapi `MyReviewEnvelope` (201), in the `MyReview` schema's shape. */
        const val CREATED =
            """{"success":true,"data":{"id":"3f0c2a1e-8a4b-4d6e-9a51-2b7c0e4d9f10","status":"pending","rating":5,"text":"Lovely","photos":[{"url":"https://cdn.bahr.eg/reviews/a.jpg","width":1600,"height":1200}],"dateISO":"2026-10-20","submittedAt":"2026-10-20T18:04:00Z","canEdit":false,"trip":{"slug":"burullus-dawn","title":"Burullus at dawn"}},"error":null}"""

        const val CONFLICT = """{"success":false,"error":{"code":"CONFLICT","message":"Booking BHR-7Q2K already has a review"}}"""
    }
}
