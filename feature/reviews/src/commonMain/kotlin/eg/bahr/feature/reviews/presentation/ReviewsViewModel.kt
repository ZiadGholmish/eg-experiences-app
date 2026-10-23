package eg.bahr.feature.reviews.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import co.touchlab.kermit.Logger
import eg.bahr.core.catalog.data.FIRST_PAGE
import eg.bahr.core.catalog.data.TripPagingConfig
import eg.bahr.core.catalog.model.MAX_STARS
import eg.bahr.core.catalog.model.RatingSummaryDto
import eg.bahr.core.catalog.model.ReviewDto
import eg.bahr.core.catalog.presentation.Throttle
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.reviews.data.ReviewQuery
import eg.bahr.feature.reviews.data.ReviewsPagingSource
import eg.bahr.feature.reviews.data.ReviewsRepository
import eg.bahr.feature.reviews.model.ReviewPageDto
import eg.bahr.feature.reviews.model.ReviewSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One of the host's trips, as the reviews page's Trip filter offers it. */
internal data class ReviewTrip(
    val slug: String,
    val title: String,
)

/**
 * The "See all reviews" page (M4-M11): a trip's reviews, or a host's.
 *
 * [hostSlug] is set for a host's reviews (null for a trip's). [tripSlug] is the trip whose reviews are
 * listed: the page's trip, or on a host's page the Trip filter's choice (null: all the host's trips).
 * [title] is the trip's title or the host's name, from the page that opened this one.
 *
 * [rating] is the summary of what is listed (the trip's or the host's reviews, never narrowed by
 * [stars]): the header and the star chips' counts. It stays from the previous answer until the new one
 * lands, so the header and the chips do not blink on every tap; a star or sort tap never changes it, a
 * Trip filter switch does.
 *
 * [notAvailable]: the trip or host has no public reviews list (NOT_FOUND: unpublished, unknown, not
 * approved). One whole-page state, with no retry: asking again gives the same answer.
 */
internal data class ReviewsUiState(
    val tripSlug: String?,
    val hostSlug: String?,
    val title: String? = null,
    val sort: ReviewSort = ReviewSort.NEWEST,
    val stars: Int? = null,
    val rating: RatingSummaryDto? = null,
    val hostTrips: List<ReviewTrip> = emptyList(),
    val notAvailable: Boolean = false,
) {
    val isHost: Boolean get() = hostSlug != null

    /** What `listReviews` is asked for: one trip's reviews when there is one, else the host's. */
    val query: ReviewQuery
        get() =
            if (tripSlug !=
                null
            ) {
                ReviewQuery(trip = tripSlug, host = null, sort, stars)
            } else {
                ReviewQuery(trip = null, host = hostSlug, sort, stars)
            }

    /**
     * The Trip filter: on a host's reviews only, and only when the host has more than one trip ("All
     * trips" and the one trip would list the same reviews).
     */
    val tripFilter: List<ReviewTrip>
        get() = if (isHost && hostTrips.size >= MIN_TRIPS_FOR_FILTER) hostTrips else emptyList()

    /** Under the heading: the filtered trip's title on a host's reviews, else the opener's. */
    val subtitle: String?
        get() = hostSlug?.let { hostTrips.firstOrNull { it.slug == tripSlug }?.title } ?: title

    /** A filter narrows the list (a star count, or one trip of the host's): its empty state is "nothing matches". */
    val isFiltered: Boolean get() = stars != null || (isHost && tripSlug != null)

    /**
     * Where the active chips sit, in reading order, for the direction a switch slides (M4-M6): the Trip
     * row first, then sort, then stars. Only one group changes per tap, so the sign of the difference is
     * that group's move. A star slot of 0 is "no star filter", 5★ is 1 … 1★ is 5 (the chips' order).
     */
    val chipOrder: Int
        get() {
            val tripSlot = tripFilter.indexOfFirst { it.slug == tripSlug } + 1
            val starSlot = stars?.let { MAX_STARS - it + 1 } ?: 0
            return (tripSlot * ReviewSort.entries.size + sort.ordinal) * STAR_SLOTS + starSlot
        }
}

/** One load of a query: [attempt] goes up on Retry, so the same query loads again from page 0. */
private data class ReviewsLoad(
    val query: ReviewQuery,
    val attempt: Int,
)

/**
 * The "See all reviews" list (M4-M11), paged from `GET /reviews?trip=` or `?host=` (Paging 3, like the
 * category page), with the sort and star chips, and on a host's reviews the Trip filter (the host's
 * trips, from the first page of `GET /trips?host=`), which switches the list to `?trip=<slug>`.
 *
 * Chip taps apply at once; taps faster than the answers collapse to the last (`flatMapLatest` cancels
 * the Pager before), and the same query twice is not asked again (`distinctUntilChanged`), as on the
 * category page (M4-M6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ReviewsViewModel(
    trip: String?,
    host: String?,
    title: String?,
    private val repository: ReviewsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewsUiState(tripSlug = if (host == null) trip else null, hostSlug = host, title = title))
    val uiState: StateFlow<ReviewsUiState> = _uiState.asStateFlow()

    /** Bumped by [retry]: the failed first page loads again. */
    private val attempts = MutableStateFlow(0)

    /** A burst of Retry taps is one request (M4-M6). */
    private val retries = Throttle(viewModelScope)

    /**
     * The reviews of the current query, a page at a time; a new query starts a new Pager at page 0.
     * Paging's presenter keeps the previous query's reviews until the new first page arrives; the screen
     * shows them dimmed under the progress bar until then (core:catalog's `PagedListStatus`).
     */
    val reviews: Flow<PagingData<ReviewDto>> =
        combine(_uiState.map { it.query }, attempts, ::ReviewsLoad)
            .distinctUntilChanged()
            .flatMapLatest { (query, _) ->
                Pager(TripPagingConfig) { ReviewsPagingSource(repository, query) { onFirstPage(query, it) } }.flow
            }.cachedIn(viewModelScope)

    init {
        host?.let(::loadHostTrips)
    }

    /** Retry, from the first page's error: loads the current query again. Throttled. */
    fun retry() = retries.attempt { attempts.update { it + 1 } }

    /** A sort chip. Tapping the active one keeps it: there is always an order. */
    fun selectSort(sort: ReviewSort) = _uiState.update { it.copy(sort = sort) }

    /** A star chip: only reviews of that many stars. Tapping the active one clears the filter. */
    fun selectStars(stars: Int) = _uiState.update { it.copy(stars = if (it.stars == stars) null else stars) }

    /**
     * The Trip filter (a host's reviews only): [slug]'s reviews, or with null ("All trips") the host's
     * again. The sort and the star filter stay; the header and the star counts become the new scope's
     * when it answers.
     */
    fun selectTrip(slug: String?) =
        _uiState.update { state ->
            if (!state.isHost || slug == state.tripSlug) state else state.copy(tripSlug = slug)
        }

    /**
     * The host's trips for the Trip filter: the first page of `GET /trips?host=` is enough for a filter
     * row (a host with more trips than a page is far off). If it fails, the filter is left out: the
     * reviews themselves do not need it.
     */
    private fun loadHostTrips(host: String) {
        viewModelScope.launch {
            when (val result = repository.listTrips(page = FIRST_PAGE, host = host)) {
                is AppResult.Success ->
                    _uiState.update { state -> state.copy(hostTrips = result.data.items.map { ReviewTrip(it.slug, it.title) }) }

                is AppResult.Failure -> log.w { "Host $host trips for the Trip filter failed: ${result.error}" }
            }
        }
    }

    private fun onFirstPage(
        query: ReviewQuery,
        result: AppResult<ReviewPageDto>,
    ) {
        _uiState.update { state ->
            // An answer for a query the user has already moved on from changes nothing.
            if (state.query != query) return@update state
            when (result) {
                is AppResult.Success -> state.copy(rating = result.data.rating)

                is AppResult.Failure ->
                    when {
                        !result.error.isNotFound() -> state

                        // One of the host's trips went away since the filter was offered: back to all of them.
                        state.isHost && query.trip != null -> {
                            log.w { "Trip ${query.trip} has no reviews list any more; showing the host's" }
                            state.copy(tripSlug = null, hostTrips = state.hostTrips.filterNot { it.slug == query.trip })
                        }

                        else -> state.copy(notAvailable = true)
                    }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("Reviews")
    }
}

/** NOT_FOUND by its code (never its message or status). */
private fun AppError.isNotFound(): Boolean = this is AppError.Api && code == ApiErrorCodes.NOT_FOUND

/** "All trips" and one trip would list the same reviews. */
private const val MIN_TRIPS_FOR_FILTER = 2

/** The star filter's slots: none, then 5★ down to 1★. */
private const val STAR_SLOTS = MAX_STARS + 1
