package com.egyptexperiences.feature.booking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egyptexperiences.core.common.locale.AppLanguage
import com.egyptexperiences.core.common.result.AppError
import com.egyptexperiences.core.common.result.AppResult
import com.egyptexperiences.core.datastore.AppSettingsStore
import com.egyptexperiences.feature.booking.data.BookingRepository
import com.egyptexperiences.feature.booking.model.GuestRequest
import com.egyptexperiences.feature.booking.model.HeldSeatsDto
import com.egyptexperiences.feature.booking.model.PlaceHoldRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

data class BookingUiState(
    val departureId: Long,
    val partySize: Int = 1,
    val guestName: String = "",
    val guestPhone: String = "",
    val isPlacingHold: Boolean = false,
    val held: HeldSeatsDto? = null,
    val holdRemaining: Duration? = null,
    val holdExpired: Boolean = false,
    val error: AppError? = null,
) {
    /**
     * The backend validates the same two things (`@NotBlank` name, a phone
     * pattern). Checking here is about not spending a round trip and a seat-hold
     * attempt on input that cannot succeed — the server stays the authority.
     */
    val canPlaceHold: Boolean
        get() =
            !isPlacingHold &&
                partySize >= MIN_PARTY_SIZE &&
                guestName.isNotBlank() &&
                guestPhone.length >= MIN_PHONE_DIGITS

    companion object {
        const val MIN_PARTY_SIZE = 1
        const val MAX_PARTY_SIZE = 50
        const val MIN_PHONE_DIGITS = 7
    }
}

class BookingViewModel(
    departureId: Long,
    private val repository: BookingRepository,
    private val settings: AppSettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingUiState(departureId = departureId))
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    fun setPartySize(size: Int) {
        _uiState.update {
            it.copy(partySize = size.coerceIn(BookingUiState.MIN_PARTY_SIZE, BookingUiState.MAX_PARTY_SIZE))
        }
    }

    fun setGuestName(name: String) = _uiState.update { it.copy(guestName = name) }

    fun setGuestPhone(phone: String) = _uiState.update { it.copy(guestPhone = phone.filter { c -> c.isDigit() || c == '+' }) }

    fun placeHold() {
        val state = _uiState.value
        if (!state.canPlaceHold) return
        _uiState.update { it.copy(isPlacingHold = true, error = null) }

        viewModelScope.launch {
            val language: AppLanguage = settings.language.first()
            val request =
                PlaceHoldRequest(
                    departureId = state.departureId,
                    partySize = state.partySize,
                    guest =
                        GuestRequest(
                            name = state.guestName.trim(),
                            phone = state.guestPhone.trim(),
                            locale = language.tag,
                        ),
                )

            when (val result = repository.placeHold(request)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(isPlacingHold = false, held = result.data, holdExpired = false)
                    }
                    startCountdown(result.data.holdExpiresAt)
                }

                is AppResult.Failure ->
                    _uiState.update { it.copy(isPlacingHold = false, error = result.error) }
            }
        }
    }

    /**
     * Ticks once a second against the server's expiry. When it reaches zero the
     * screen does not assume the hold is dead — it marks it expired and lets the
     * user go back to the date picker, where the next read tells the truth.
     */
    private fun startCountdown(holdExpiresAt: String?) {
        viewModelScope.launch {
            while (true) {
                val remaining = HoldCountdown.remaining(holdExpiresAt)
                _uiState.update {
                    it.copy(holdRemaining = remaining, holdExpired = remaining == Duration.ZERO)
                }
                if (remaining == null || remaining == Duration.ZERO) return@launch
                delay(TICK_MILLIS)
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
