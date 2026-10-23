package eg.bahr.feature.reviews.data

import eg.bahr.core.catalog.data.TRIP_PAGE_SIZE
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.reviews.model.ReviewPageDto
import eg.bahr.feature.reviews.model.ReviewSort
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** "See all reviews" (split from feature:trips, M3-M16). Needs no token: reviews are public. */
internal class ReviewsApiService(
    private val client: HttpClient,
) {
    /**
     * `listReviews` (M4-M11): a page of every review of the trip [trip] or of the host [host] (exactly one
     * is given; the other is left out), in [sort] order, narrowed to reviews of exactly [stars] stars when
     * set. NOT_FOUND for a trip that is not published and a host with no public page.
     */
    suspend fun reviews(
        trip: String?,
        host: String?,
        sort: ReviewSort,
        stars: Int?,
        page: Int = 0,
        size: Int = PAGE_SIZE,
    ): AppResult<ReviewPageDto> =
        callApi {
            client.get("reviews") {
                // A null value is not sent.
                parameter("trip", trip)
                parameter("host", host)
                parameter("sort", sort.wire)
                parameter("stars", stars)
                parameter("page", page)
                parameter("size", size)
            }
        }

    companion object {
        /** The one page size every list reads at (core:catalog's, where `GET /trips` lives since M4-M1c). */
        const val PAGE_SIZE = TRIP_PAGE_SIZE
    }
}
