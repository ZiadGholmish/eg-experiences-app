package eg.bahr.feature.reviews.data

import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.model.TripPageDto
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.reviews.model.ReviewPageDto
import eg.bahr.feature.reviews.model.ReviewSort

/**
 * Hand-written fake: each call answers from a lambda the test sets, and every request is
 * recorded so the test can assert what the view model asked for. Unset calls fail loudly with a
 * generic error rather than returning a default that could hide a wrong call.
 *
 * One fake per repository interface, in that feature's commonTest (split from feature:trips' fake, M3-M16: a fake
 * of a core:catalog type cannot live in core:testing, which may not depend on core:catalog).
 */
internal class FakeReviewsRepository(
    var listTrips: suspend (page: Int) -> AppResult<TripPageDto> = { unset() },
    /** The narrowed list (category page, search, a host's trips); defaults to [listTrips], which ignores the query. */
    var listFiltered: (suspend (query: TripQuery) -> AppResult<TripPageDto>)? = null,
    var reviews: suspend (request: ReviewRequest) -> AppResult<ReviewPageDto> = { unset() },
) : ReviewsRepository {
    val requestedPages = mutableListOf<Int>()
    val requestedQueries = mutableListOf<TripQuery>()
    val reviewRequests = mutableListOf<ReviewRequest>()

    override suspend fun listTrips(
        page: Int,
        category: String?,
        filter: String?,
        q: String?,
        host: String?,
    ): AppResult<TripPageDto> {
        val query = TripQuery(page, category, filter, q, host)
        requestedPages += page
        requestedQueries += query
        return listFiltered?.invoke(query) ?: listTrips.invoke(page)
    }

    override suspend fun reviews(
        trip: String?,
        host: String?,
        sort: ReviewSort,
        stars: Int?,
        page: Int,
    ): AppResult<ReviewPageDto> {
        val request = ReviewRequest(trip, host, sort, stars, page)
        reviewRequests += request
        return reviews.invoke(request)
    }

    private companion object {
        fun unset(): AppResult.Failure = AppResult.Failure(AppError.Unknown("FakeReviewsRepository: call not stubbed"))
    }
}

/** One `GET /trips` call as the fake saw it. */
internal data class TripQuery(
    val page: Int,
    val category: String? = null,
    val filter: String? = null,
    val q: String? = null,
    val host: String? = null,
)

/** One `GET /reviews` call as the fake saw it. */
internal data class ReviewRequest(
    val trip: String?,
    val host: String?,
    val sort: ReviewSort,
    val stars: Int?,
    val page: Int,
)

/**
 * Test data in the contract's shapes. Copy is Arabic because Arabic is what most users
 * see. No image URLs: a screenshot must not depend on whether a local MinIO happens to be running.
 */
internal object TripFixtures {
    fun trip(
        n: Int,
        title: String = "رحلة رقم $n",
        priceEgp: Long = 450,
        durationLabel: String = "05:00 → 22:00",
        badge: BadgeDto? = null,
        nextDeparture: NextDepartureDto? = null,
        nights: Int = 0,
    ) = TripCardDto(
        slug = "trip-$n",
        title = title,
        durationLabel = durationLabel,
        nights = nights,
        price = MoneyDto(amount = priceEgp, currencyCode = "EGP"),
        badge = badge,
        nextDeparture = nextDeparture,
    )

    fun page(
        trips: List<TripCardDto>,
        page: Int = 0,
        totalPages: Int = 1,
        facets: List<FacetDto> = emptyList(),
        totalItems: Long = trips.size.toLong(),
    ) = AppResult.Success(
        TripPageDto(items = trips, page = page, size = trips.size, totalItems = totalItems, totalPages = totalPages, facets = facets),
    )
}
