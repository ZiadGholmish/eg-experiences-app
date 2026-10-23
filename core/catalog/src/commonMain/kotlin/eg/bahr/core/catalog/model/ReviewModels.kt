package eg.bahr.core.catalog.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * The contract's `Review` and `RatingSummary` shapes, field for field with `../docs/api/openapi.yaml`: the trip page's
 * preview (`TripDetail.reviews.items`), the host page (`HostProfile`) and the full list (`ReviewPage`) all carry them.
 * Moved here from feature:trips when it was split (M3-M16), unchanged.
 */

/**
 * One review, the same shape on the trip page, the host page and the full list (`listReviews`). [tone]
 * tints the author's initial (the contract's `Tone`). [body] is absent for a review that is a rating
 * only; [rating] (1–5 stars, M4-B8) is absent from older servers, and then no stars are drawn.
 */
@Serializable
data class ReviewDto(
    val author: String? = null,
    val dateISO: LocalDate? = null,
    val body: String? = null,
    val partyLabel: String? = null,
    val tone: String? = null,
    val rating: Int? = null,
)

/**
 * openapi `RatingSummary`: every review of the host's published trips. [average] is absent when
 * [count] is 0. [distribution] is five entries, 5 stars first; read it through [stars], which does not
 * rely on that order.
 */
@Serializable
data class RatingSummaryDto(
    val average: Double? = null,
    val count: Int = 0,
    val distribution: List<StarCountDto> = emptyList(),
)

/** One star bucket: how many reviews gave [stars] stars. */
@Serializable
data class StarCountDto(
    val stars: Int,
    val count: Int,
)

/**
 * The five bars, 5 stars down to 1, each with its count: a bucket the server left out counts 0, and
 * one outside 1..5 is ignored, so the bars are always the same five whatever the payload's order.
 */
val RatingSummaryDto.stars: List<StarCountDto>
    get() {
        val counts = distribution.filter { it.stars in MIN_STARS..MAX_STARS }.associate { it.stars to it.count }
        return (MAX_STARS downTo MIN_STARS).map { StarCountDto(stars = it, count = counts[it] ?: 0) }
    }

const val MAX_STARS = 5
const val MIN_STARS = 1
