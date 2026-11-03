package eg.bahr.feature.reviews.model

import eg.bahr.core.catalog.model.ImageDto
import kotlinx.serialization.Serializable

/*
 * Review a trip (M3-M4): `submitReview`'s shapes, field for field with `../docs/api/openapi.yaml`.
 */

/**
 * openapi `ReviewDraft`, `submitReview`'s `review` part: [rating] 1–5 (required), [text] optional. Blank text is sent
 * as no text (left out), which the server would also read as none.
 */
@Serializable
internal data class ReviewDraftDto(
    val rating: Int,
    val text: String? = null,
)

/**
 * openapi `MyReview`, what `submitReview` answers with (201): the stored review, `pending` until the back-office
 * approves it. Only what this step reads is required; the rest is nullable with a default so a later server's
 * change cannot fail the decode.
 */
@Serializable
internal data class MyReviewDto(
    val id: String,
    val status: String,
    val rating: Int,
    val text: String? = null,
    val photos: List<ImageDto> = emptyList(),
    val dateISO: String? = null,
    val submittedAt: String? = null,
    val moderatedAt: String? = null,
    val reason: String? = null,
    val canEdit: Boolean = false,
)

/** The server's limits on a review (openapi `submitReview`: "server configuration"), checked here before sending. */
internal object ReviewLimits {
    /** `ReviewDraft.text`'s `maxLength`. */
    const val MAX_TEXT = 2000

    /** "at most 5 per review". */
    const val MAX_PHOTOS = 5
}
