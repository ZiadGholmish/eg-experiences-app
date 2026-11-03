package eg.bahr.feature.reviews.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.reviews.model.MyReviewDto

/** Hand-written fake: [submit] answers each send; every send is recorded in [requests]. Defaults to a pending review. */
internal class FakeWriteReviewRepository(
    var submit: suspend (SubmitRequest) -> AppResult<MyReviewDto> = { AppResult.Success(ReviewFixtures.pending()) },
) : WriteReviewRepository {
    val requests = mutableListOf<SubmitRequest>()

    override suspend fun submit(
        ref: String,
        rating: Int,
        text: String?,
        photos: List<ByteArray>,
    ): AppResult<MyReviewDto> {
        val request = SubmitRequest(ref, rating, text, photos.size).also { it.jpegs = photos }
        requests += request
        return submit.invoke(request)
    }
}

/** One send as the fake saw it: compared by its fields and the number of photos; [jpegs] are the photos themselves. */
internal data class SubmitRequest(
    val ref: String,
    val rating: Int,
    val text: String?,
    val photos: Int,
) {
    var jpegs: List<ByteArray> = emptyList()
}

internal object ReviewFixtures {
    /** What `submitReview` answers with: the stored review, pending moderation. */
    fun pending(rating: Int = 5) =
        MyReviewDto(id = "3f0c2a1e-8a4b-4d6e-9a51-2b7c0e4d9f10", status = "pending", rating = rating, canEdit = false)
}
