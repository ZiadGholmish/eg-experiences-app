package eg.bahr.feature.booking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.model.BookingDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class BookingConfirmedUiState(
    val isLoading: Boolean = true,
    /** openapi `Booking` (the contract shape since MV-M4; see [BookingDto]). */
    val booking: BookingDto? = null,
    val error: AppError? = null,
)

internal class BookingConfirmedViewModel(
    private val ref: String,
    private val repository: BookingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingConfirmedUiState())
    val uiState: StateFlow<BookingConfirmedUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private var refreshJob: Job? = null

    fun load() {
        refreshJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.bookingByRef(ref)) {
                is AppResult.Success ->
                    _uiState.update { it.copy(isLoading = false, booking = result.data) }

                is AppResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    /**
     * The screen is back in front (MV-M13: from the review page, the calendar, the app brought forward): the booking is
     * read again quietly, so a review just sent takes "Rate your trip" away and a status that moved shows. No skeleton:
     * the ticket stays as it is until the new read lands, and stays as it was if that read fails. With nothing shown
     * yet (still loading, or an error on screen) it is the full [load] or nothing.
     */
    fun refresh() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.booking == null) return load()
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                val result = repository.bookingByRef(ref)
                if (result is AppResult.Success) _uiState.update { it.copy(booking = result.data) }
            }
    }
}
