package eg.bahr.feature.reviews.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.reviews.model.MyReviewDto
import eg.bahr.feature.reviews.model.ReviewDraftDto

/** Review a trip (M3-M4). An interface so view-model tests hand in a fake. */
internal interface WriteReviewRepository {
    /** Sends booking [ref]'s review: [rating] 1–5, [text] or null, [photos] as compressed JPEGs. */
    suspend fun submit(
        ref: String,
        rating: Int,
        text: String?,
        photos: List<ByteArray>,
    ): AppResult<MyReviewDto>
}

internal class DefaultWriteReviewRepository(
    private val api: WriteReviewApiService,
) : WriteReviewRepository {
    override suspend fun submit(
        ref: String,
        rating: Int,
        text: String?,
        photos: List<ByteArray>,
    ): AppResult<MyReviewDto> = api.submit(ref, ReviewDraftDto(rating = rating, text = text), photos)
}
