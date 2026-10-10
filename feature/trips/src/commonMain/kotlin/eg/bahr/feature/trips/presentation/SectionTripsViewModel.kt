package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.data.SectionTripsPagingSource
import eg.bahr.feature.trips.data.TripPagingConfig
import eg.bahr.feature.trips.data.TripRepository
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
)

/** Every trip of one Home `trips` section, paged, in the row's order. */
internal class SectionTripsViewModel(
    sectionId: String,
    title: String?,
    repository: TripRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SectionTripsUiState(title = title))
    val uiState: StateFlow<SectionTripsUiState> = _uiState.asStateFlow()

    val trips: Flow<PagingData<TripCardDto>> =
        Pager(TripPagingConfig) {
            SectionTripsPagingSource(repository, sectionId) { result ->
                if (result is AppResult.Success) _uiState.update { it.copy(totalItems = result.data.totalItems) }
            }
        }.flow
            .distinctTrips()
            .cachedIn(viewModelScope)
}
