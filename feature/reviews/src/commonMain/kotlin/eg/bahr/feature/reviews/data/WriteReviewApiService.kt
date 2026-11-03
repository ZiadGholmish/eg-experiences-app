package eg.bahr.feature.reviews.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.reviews.model.MyReviewDto
import eg.bahr.feature.reviews.model.ReviewDraftDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

/**
 * `submitReview` (M3-B7): `POST /me/bookings/{ref}/review` as `multipart/form-data`, signed in (core:network's
 * session adds the token). Never retried here: a resend after a lost answer would meet CONFLICT, which the view model
 * reads as "already reviewed".
 */
internal class WriteReviewApiService(
    private val client: HttpClient,
) {
    /**
     * [draft] goes as the `review` part, which the contract requires to carry its own `application/json` content type
     * (else UNSUPPORTED_MEDIA_TYPE); each of [photos] (JPEG, compressed on the device) as one `photos` part with a file
     * name, which is what makes a server read a part as a file rather than a text field.
     */
    suspend fun submit(
        ref: String,
        draft: ReviewDraftDto,
        photos: List<ByteArray>,
    ): AppResult<MyReviewDto> =
        callApi {
            client.post("me/bookings/${ref.encodeURLPathPart()}/review") {
                // Up to five photos on a slow mobile link take longer than a JSON call's limit.
                timeout { requestTimeoutMillis = UPLOAD_TIMEOUT_MILLIS }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                REVIEW_PART,
                                partJson.encodeToString(ReviewDraftDto.serializer(), draft),
                                Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
                            )
                            photos.forEachIndexed { index, jpeg ->
                                append(
                                    PHOTOS_PART,
                                    jpeg,
                                    Headers.build {
                                        append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                                        append(HttpHeaders.ContentDisposition, "filename=\"photo-${index + 1}.jpg\"")
                                    },
                                )
                            }
                        },
                    ),
                )
            }
        }

    companion object {
        /** Two minutes: five ~0.5 MB photos at a weak 3G rate, with room to spare. */
        const val UPLOAD_TIMEOUT_MILLIS = 120_000L

        const val REVIEW_PART = "review"
        const val PHOTOS_PART = "photos"

        /** An absent text is left out rather than sent as `null` (both mean "no text" to the server). */
        private val partJson = Json { explicitNulls = false }
    }
}
