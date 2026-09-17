package com.egyptexperiences.feature.booking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egyptexperiences.core.common.result.AppError
import com.egyptexperiences.core.common.result.AppResult
import com.egyptexperiences.feature.booking.data.BookingRepository
import com.egyptexperiences.feature.booking.model.BookingDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingConfirmedUiState(
    val isLoading: Boolean = true,
    val booking: BookingDto? = null,
    val error: AppError? = null,
)

class BookingConfirmedViewModel(
    private val ref: String,
    private val repository: BookingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingConfirmedUiState())
    val uiState: StateFlow<BookingConfirmedUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
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
}
