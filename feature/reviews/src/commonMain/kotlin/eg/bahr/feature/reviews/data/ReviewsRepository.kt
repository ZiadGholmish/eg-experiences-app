package eg.bahr.feature.reviews.data

import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.reviews.model.ReviewPageDto
import eg.bahr.feature.reviews.model.ReviewSort

/**
 * The reviews list's data. A host's trips (the Trip filter) are `listTrips` with `host`, from core:catalog's
 * [TripListRepository]. An interface so view-model tests can hand in a fake (the project uses hand-written fakes,
 * no mocking library).
 */
internal interface ReviewsRepository : TripListRepository {
    /**
     * One page of a trip's ([trip]) or a host's ([host]) reviews, the "See all reviews" list (M4-M11).
     * Exactly one of the two is given.
     */
    suspend fun reviews(
        trip: String?,
        host: String?,
        sort: ReviewSort,
        stars: Int?,
        page: Int,
    ): AppResult<ReviewPageDto>
}

/** A pass-through to [api], with `GET /trips` from [lists]. */
internal class DefaultReviewsRepository(
    private val api: ReviewsApiService,
    private val lists: TripListRepository,
) : ReviewsRepository,
    TripListRepository by lists {
    override suspend fun reviews(
        trip: String?,
        host: String?,
        sort: ReviewSort,
        stars: Int?,
        page: Int,
    ): AppResult<ReviewPageDto> = api.reviews(trip = trip, host = host, sort = sort, stars = stars, page = page)
}
