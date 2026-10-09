package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripDetailDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sticky bar's button, in the handoff's three states: "Choose a date" → "Continue" → "Sold out". */
internal enum class TripCta { ChooseDate, Continue, SoldOut }

internal data class TripDetailUiState(
    /** The list card for this trip, if the user came from the list: text to show while [trip] loads. */
    val preview: TripCardDto? = null,
    val trip: TripDetailDto? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    /** From `GET /trips/{slug}/departures`; null until it answers, or when it failed. */
    val liveDepartures: List<DepartureDto>? = null,
    val liveDeparturesLoading: Boolean = true,
    val selectedDepartureId: String? = null,
) {
    /**
     * The dates to show: the live list when it has answered, else the trip's own `dates` (the same
     * shape, read with the trip), so a failed departures call still leaves the page bookable.
     */
    val departures: List<DepartureDto>
        get() = liveDepartures ?: trip?.dates.orEmpty()

    /** No date is known yet and one is still on its way: the availability row shimmers. */
    val departuresLoading: Boolean
        get() = departures.isEmpty() && (liveDeparturesLoading || isLoading)

    val selectedDeparture: DepartureDto?
        get() = departures.firstOrNull { it.id == selectedDepartureId }

    val cta: TripCta
        get() {
            val selected = selectedDeparture ?: return TripCta.ChooseDate
            return if (selected.bookable) TripCta.Continue else TripCta.SoldOut
        }

    /** The date "Continue" books. Null unless the selected date is bookable, so sold out never continues. */
    val continueDepartureId: String?
        get() = selectedDeparture?.takeIf { it.bookable }?.id

    /**
     * Under a selected sold-out date, the date the notice points to instead: the next bookable one
     * after it, else any bookable one. Null when nothing has seats.
     */
    val alternative: DepartureDto?
        get() {
            val full = selectedDeparture?.takeIf { it.soldOut } ?: return null
            val open = departures.filter { it.bookable }
            return open.firstOrNull { it.date > full.date } ?: open.firstOrNull()
        }
}

/**
 * A date can be picked when it can be booked, or when it is sold out: picking a full date shows the
 * sold-out notice (HANDOFF screen 3). A date that is closed for another reason (cancelled, past its
 * cutoff) is shown but cannot be picked: the contract does not say why, so there is nothing to tell.
 */
internal fun DepartureDto.isSelectable(): Boolean = bookable || soldOut

internal class TripDetailViewModel(
    private val slug: String,
    private val repository: TripRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripDetailUiState(preview = repository.cachedCard(slug)))
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * Two independent reads, each landing on its own: the trip renders as soon as it arrives, and
     * the date cards (live seat counts) fill in when theirs does.
     */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null, liveDeparturesLoading = true) }
        viewModelScope.launch {
            when (val trip = repository.tripBySlug(slug)) {
                is AppResult.Success ->
                    _uiState.update { it.copy(isLoading = false, trip = trip.data).keepingSelection() }
                is AppResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, error = trip.error) }
            }
        }
        viewModelScope.launch {
            val live = (repository.departuresFor(slug) as? AppResult.Success)?.data
            // A failure is not an error screen: the trip's own `dates` stand in (see `departures`).
            _uiState.update { it.copy(liveDeparturesLoading = false, liveDepartures = live).keepingSelection() }
        }
    }

    /** Tapping the selected date again clears it; a date that cannot be picked is ignored. */
    fun selectDeparture(departureId: String) {
        _uiState.update { state ->
            val departure = state.departures.firstOrNull { it.id == departureId }
            when {
                departure == null || !departure.isSelectable() -> state
                state.selectedDepartureId == departureId -> state.copy(selectedDepartureId = null)
                else -> state.copy(selectedDepartureId = departureId)
            }
        }
    }

    /** After a refresh, a selection whose date is gone (or no longer pickable) is dropped. */
    private fun TripDetailUiState.keepingSelection(): TripDetailUiState {
        val selected = selectedDepartureId ?: return this
        val stillThere = departures.any { it.id == selected && it.isSelectable() }
        return if (stillThere) this else copy(selectedDepartureId = null)
    }
}
