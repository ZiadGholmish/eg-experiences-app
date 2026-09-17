package com.egyptexperiences.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egyptexperiences.core.common.result.AppError
import com.egyptexperiences.core.common.result.AppResult
import com.egyptexperiences.feature.trips.data.TripRepository
import com.egyptexperiences.feature.trips.model.DepartureDto
import com.egyptexperiences.feature.trips.model.TripDetailDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripDetailUiState(
    val isLoading: Boolean = true,
    val trip: TripDetailDto? = null,
    val departures: List<DepartureDto> = emptyList(),
    val selectedDepartureId: Long? = null,
    val error: AppError? = null,
) {
    val selectedDeparture: DepartureDto?
        get() = departures.firstOrNull { it.id == selectedDepartureId }

    /** Sold out as a whole: every listed departure is unbookable. */
    val allSoldOut: Boolean
        get() = departures.isNotEmpty() && departures.none { it.bookable }
}

class TripDetailViewModel(
    private val slug: String,
    private val repository: TripRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Two independent reads; run them together so the screen shows in
            // one round trip rather than two.
            val tripCall = async { repository.tripBySlug(slug) }
            val departuresCall = async { repository.departuresFor(slug) }

            when (val trip = tripCall.await()) {
                is AppResult.Failure -> {
                    departuresCall.await()
                    _uiState.update { it.copy(isLoading = false, error = trip.error) }
                }

                is AppResult.Success -> {
                    // Departures failing alone is not fatal: the trip still
                    // renders, with the date picker showing its own error.
                    val departures =
                        (departuresCall.await() as? AppResult.Success)?.data ?: emptyList()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            trip = trip.data,
                            departures = departures,
                            selectedDepartureId = departures.firstOrNull { d -> d.bookable }?.id,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun selectDeparture(departureId: Long) {
        _uiState.update { it.copy(selectedDepartureId = departureId) }
    }
}
