package eg.bahr.feature.trips.model

import kotlinx.serialization.Serializable

/*
 * Wire shapes for `GET /api/v1/reviews` (`listReviews`, M4-B8), field for field with
 * `../docs/api/openapi.yaml`. The items are the trip page's `Review` shape ([ReviewDto]).
 */

/**
 * openapi `ReviewPage`: a page of a trip's or a host's reviews. [rating] summarises the whole scope (the
 * trip's or the host's reviews) whatever `stars` narrowed the page to: it is what the star chips count
 * from, and the page's header.
 */
@Serializable
internal data class ReviewPageDto(
    val items: List<ReviewDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val rating: RatingSummaryDto = RatingSummaryDto(),
)

/** `listReviews`' `sort` values, in the order the chips show them. [NEWEST] is the server's default. */
internal enum class ReviewSort(
    val wire: String,
) {
    NEWEST("newest"),
    HIGHEST("highest"),
    LOWEST("lowest"),
}
