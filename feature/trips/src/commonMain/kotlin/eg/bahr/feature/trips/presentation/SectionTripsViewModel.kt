package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.data.SectionTripsPagingSource
import eg.bahr.feature.trips.data.TripPagingConfig
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.model.TripCardDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A Home row's "See all" (M4-M1b). [title] is the row's, carried through navigation because
 * `GET /home/sections/{id}/trips` does not return it; [totalItems] is the list's length from its
 * first page.
 */
internal data class SectionTripsUiState(
    val title: String?,
    val totalItems: Long? = null,
    val waitlistTags: WaitlistTags = WaitlistTags.None,
)

/** Every trip of one Home `trips` section, paged, in the row's order. */
internal class SectionTripsViewModel(
    sectionId: String,
    title: String?,
    repository: TripRepository,
    waitlists: WaitlistMemory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionTripsUiState(title = title))
    val uiState: StateFlow<SectionTripsUiState> = _uiState.asStateFlow()

    /** Lets [retry] load the list again from page 0: each invalidation makes a fresh source. */
    private val sources =
        InvalidatingPagingSourceFactory {
            SectionTripsPagingSource(repository, sectionId) { result ->
                if (result is AppResult.Success) _uiState.update { it.copy(totalItems = result.data.totalItems) }
            }
        }

    /** A burst of Retry taps is one request (M4-M6). */
    private val retries = Throttle(viewModelScope)

    val trips: Flow<PagingData<TripCardDto>> =
        // A lambda, not the factory itself: common metadata does not see the factory as a function type.
        Pager(TripPagingConfig, pagingSourceFactory = { sources() })
            .flow
            .distinctTrips()
            .cachedIn(viewModelScope)

    /** Retry, from the first page's error: loads the list again. Throttled. */
    fun retry() = retries.attempt { sources.invalidate() }

    init {
        collectWaitlistTags(waitlists) { tags -> _uiState.update { it.copy(waitlistTags = tags) } }
    }
}
