package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import co.touchlab.kermit.Logger
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.trips.data.TripListPagingSource
import eg.bahr.feature.trips.data.TripPagingConfig
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.model.FILTER_ALL
import eg.bahr.feature.trips.model.FacetDto
import eg.bahr.feature.trips.model.FacetType
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripPageDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The category page (M4-M1b): `GET /trips?category=<key>&filter=<key>`, paged.
 *
 * [category] is the category asked for, or null once the server has turned it down (a stale key, see
 * [CategoryTripsViewModel]); [filter] is the active filter chip's key, null for "all". [facets] and
 * [totalItems] come from the current query's first page, and stay from the previous query until the
 * new one answers, so the chips do not blink out on every tap.
 *
 * [fallbackTitle] is what the opener already knew (a chip's label, a row's title), shown until the
 * facets name the category. [categoryAccepted] is true once a page of [category] has loaded, so a
 * later 400 can only be about the filter.
 */
internal data class CategoryTripsUiState(
    val category: String?,
    val filter: String? = null,
    val facets: List<FacetDto> = emptyList(),
    val totalItems: Long? = null,
    val fallbackTitle: String? = null,
    val categoryAccepted: Boolean = false,
) {
    /** The category's own chip: its label, icon and tone make the header. Matched on type and key. */
    val categoryFacet: FacetDto?
        get() = category?.let { key -> facets.firstOrNull { it.type == FacetType.CATEGORY && it.key == key } }

    /** The filter chips, in the served order. Category chips are not drawn here: the page is one category. */
    val filterChips: List<FacetDto>
        get() = facets.filter { it.type == FacetType.FILTER && !it.key.isNullOrBlank() }

    /** Whether [chip] is the active filter. Local, so a tap shows at once, before the server confirms it. */
    fun isSelected(chip: FacetDto): Boolean = chip.key == (filter ?: FILTER_ALL)
}

/** What one query asks `GET /trips` for. */
private data class CategoryQuery(
    val category: String?,
    val filter: String?,
)

/**
 * A stale or unknown key (a banner or link authored before a category was removed) is answered with
 * 400 VALIDATION_FAILED, which does not say which key it refused (and the message is never parsed).
 * Once the category has loaded, only the filter can be the stale one, so only the filter is dropped;
 * before that, both are, and the page shows every trip instead of an error. Each step drops
 * something, so it ends after at most two retries with the unfiltered list (or its error).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CategoryTripsViewModel(
    category: String,
    title: String?,
    private val repository: TripRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryTripsUiState(category = category, fallbackTitle = title))
    val uiState: StateFlow<CategoryTripsUiState> = _uiState.asStateFlow()

    /**
     * The trips of the current query, a page at a time. A new query (a chip tap, the fallback) starts
     * a new Pager, so the list starts over at page 0.
     */
    val trips: Flow<PagingData<TripCardDto>> =
        _uiState
            .map { CategoryQuery(it.category, it.filter) }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                Pager(TripPagingConfig) {
                    TripListPagingSource(repository, query.category, query.filter) { onFirstPage(query, it) }
                }.flow
                // Paging's presenter keeps the previous query's cards until the new first page arrives;
                // the screen reads the refresh state first, so a switch shows loading, then the new
                // cards or the error with its retry (see `PagedListStatus`).
            }.distinctTrips()
            .cachedIn(viewModelScope)

    /** A filter chip tapped. Tapping the active one (or "all") clears the filter. */
    fun selectFilter(key: String) {
        _uiState.update { state ->
            val cleared = key == FILTER_ALL || key == state.filter
            val filter = if (cleared) null else key
            // The count belongs to the query; the chips stay (with their old counts) until it answers.
            if (filter == state.filter) state else state.copy(filter = filter, totalItems = null)
        }
    }

    private fun onFirstPage(
        query: CategoryQuery,
        result: AppResult<TripPageDto>,
    ) {
        _uiState.update { state ->
            // An answer for a query the user has already moved on from changes nothing.
            if (CategoryQuery(state.category, state.filter) != query) return@update state
            when (result) {
                is AppResult.Success ->
                    state.copy(
                        facets = result.data.facets,
                        totalItems = result.data.totalItems,
                        categoryAccepted = state.categoryAccepted || query.category != null,
                    )

                is AppResult.Failure ->
                    when {
                        // Any other failure is the list's error state, with its retry.
                        !result.error.isValidationFailure() -> state

                        // The category has loaded before, so the server refused the filter: drop just that.
                        query.filter != null && state.categoryAccepted -> {
                            log.w { "Filter ${query.filter} refused; showing the whole category" }
                            state.copy(filter = null, totalItems = null)
                        }

                        query.category != null || query.filter != null -> {
                            log.w { "Category ${query.category} / filter ${query.filter} refused; showing every trip" }
                            state.copy(category = null, filter = null, totalItems = null)
                        }

                        // Nothing left to drop: the unfiltered list's error, never another request.
                        else -> state
                    }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("CategoryTrips")
    }
}

private fun AppError.isValidationFailure(): Boolean = this is AppError.Api && code == ApiErrorCodes.VALIDATION_FAILED
