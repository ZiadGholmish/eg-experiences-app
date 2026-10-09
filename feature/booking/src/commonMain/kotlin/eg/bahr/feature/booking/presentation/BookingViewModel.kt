package eg.bahr.feature.booking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.model.BookingDepartureDto
import eg.bahr.feature.booking.model.BookingTripDto
import eg.bahr.feature.booking.model.GuestRequest
import eg.bahr.feature.booking.model.HeldSeatsDto
import eg.bahr.feature.booking.model.PlaceHoldRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class BookingUiState(
    val trip: BookingTripDto? = null,
    val isLoading: Boolean = true,
    /** The trip itself failed to load: the screen shows the error with a retry. */
    val loadError: AppError? = null,
    /** From `GET /trips/{slug}/departures`; null until it answers, or when it failed. */
    val liveDepartures: List<BookingDepartureDto>? = null,
    val selectedDepartureId: String? = null,
    val partySize: Int = MIN_PARTY_SIZE,
    val guestName: String = "",
    val guestPhone: String = "",
    val isPlacingHold: Boolean = false,
    /** Why the last hold failed; cleared by the next edit. */
    val holdError: AppError? = null,
    /** Set once a hold is placed, until the screen has navigated on ([BookingViewModel.onHeldHandled]). */
    val held: HeldSeatsDto? = null,
) {
    /**
     * The live dates when they have answered, else the trip's own `dates` (the same shape, read with
     * the trip), so a failed departures call still leaves the screen bookable.
     */
    val departures: List<BookingDepartureDto>
        get() = liveDepartures ?: trip?.dates.orEmpty()

    val selectedDeparture: BookingDepartureDto?
        get() = departures.firstOrNull { it.id == selectedDepartureId }

    /** The stepper's upper bound, from the server's policy (`policy.maxPartySize`). */
    val maxPartySize: Int
        get() = trip?.policy?.maxPartySize?.takeIf { it >= MIN_PARTY_SIZE } ?: FALLBACK_MAX_PARTY_SIZE

    val canDecreaseParty: Boolean get() = partySize > MIN_PARTY_SIZE
    val canIncreaseParty: Boolean get() = partySize < maxPartySize

    val isNameValid: Boolean
        get() = guestName.isNotBlank() && guestName.trim().length <= NAME_MAX_LENGTH

    /** The contract's `Phone` pattern, so a typo is caught before it costs a seat-hold attempt. */
    val isPhoneValid: Boolean
        get() = PHONE_PATTERN.matches(guestPhone)

    /**
     * The server checks all of this again and stays the authority; checking here is about not
     * spending a round trip on input that cannot succeed.
     */
    val canPlaceHold: Boolean
        get() =
            !isPlacingHold &&
                selectedDeparture?.isOpen == true &&
                partySize in MIN_PARTY_SIZE..maxPartySize &&
                isNameValid &&
                isPhoneValid

    companion object {
        const val MIN_PARTY_SIZE = 1

        /**
         * Only when the trip does not state `policy.maxPartySize`: the handoff's 6. The server
         * answers `VALIDATION_FAILED` for a party over its real limit either way.
         */
        const val FALLBACK_MAX_PARTY_SIZE = 6

        /** `PlaceHoldRequest.guest.name` `maxLength`. */
        const val NAME_MAX_LENGTH = 80

        /** The longest value the `Phone` pattern accepts: a `+` and 15 digits. */
        const val PHONE_MAX_LENGTH = 16

        /** openapi `Phone`. */
        val PHONE_PATTERN = Regex("""^\+?[0-9]{7,15}$""")
    }
}

/**
 * Date + party (HANDOFF screen 4): pick one of the trip's dates, the party size and the lead
 * contact, then place the 15-minute seat hold.
 *
 * [departureId] is the date picked on the trip page; it starts selected if it can still be booked.
 */
internal class BookingViewModel(
    private val slug: String,
    departureId: String,
    private val repository: BookingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingUiState(selectedDepartureId = departureId))
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** The trip and its live dates, each landing on its own. */
    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val trip = repository.tripBySlug(slug)) {
                is AppResult.Success ->
                    _uiState.update { it.copy(isLoading = false, trip = trip.data).withValidParty().keepingSelection() }
                is AppResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, loadError = trip.error) }
            }
        }
        refreshDepartures()
    }

    /** Only a date that can be booked is picked; tapping another moves the selection. */
    fun selectDeparture(departureId: String) {
        _uiState.update { state ->
            val departure = state.departures.firstOrNull { it.id == departureId }
            if (departure?.isOpen == true) state.copy(selectedDepartureId = departureId, holdError = null) else state
        }
    }

    fun increaseParty() = _uiState.update { it.copy(partySize = (it.partySize + 1).coerceAtMost(it.maxPartySize), holdError = null) }

    fun decreaseParty() =
        _uiState.update {
            it.copy(partySize = (it.partySize - 1).coerceAtLeast(BookingUiState.MIN_PARTY_SIZE), holdError = null)
        }

    fun setGuestName(name: String) = _uiState.update { it.copy(guestName = name.take(BookingUiState.NAME_MAX_LENGTH), holdError = null) }

    /**
     * Keeps digits and a leading `+` only, so "010 1234 5678" and "+20 10…" both become what the
     * contract's `Phone` pattern accepts. The server compares phones by their digits.
     */
    fun setGuestPhone(phone: String) {
        val digits = phone.filter { it.isDigit() }
        val cleaned = (if (phone.trimStart().startsWith('+')) "+$digits" else digits).take(BookingUiState.PHONE_MAX_LENGTH)
        _uiState.update { it.copy(guestPhone = cleaned, holdError = null) }
    }

    fun placeHold() {
        val state = _uiState.value
        val departure = state.selectedDeparture
        if (!state.canPlaceHold || departure == null) return
        _uiState.update { it.copy(isPlacingHold = true, holdError = null) }

        viewModelScope.launch {
            val request =
                PlaceHoldRequest(
                    departureId = departure.id,
                    partySize = state.partySize,
                    guest = GuestRequest(name = state.guestName.trim(), phone = state.guestPhone),
                )
            when (val result = repository.placeHold(request)) {
                is AppResult.Success ->
                    _uiState.update { it.copy(isPlacingHold = false, held = result.data) }

                is AppResult.Failure -> {
                    _uiState.update { it.copy(isPlacingHold = false, holdError = result.error) }
                    // The seat counts on screen were wrong (someone else took the seats, or booking
                    // closed): re-read them rather than let the user try the same date again blind.
                    if (result.error.isStaleDates()) refreshDepartures()
                }
            }
        }
    }

    /** The screen has moved on to the held seats; going back here must not navigate again. */
    fun onHeldHandled() = _uiState.update { it.copy(held = null) }

    private fun refreshDepartures() {
        viewModelScope.launch {
            val live = (repository.departuresFor(slug) as? AppResult.Success)?.data
            // A failure is not an error screen: the last list, or the trip's own dates, stand in.
            _uiState.update { it.copy(liveDepartures = live ?: it.liveDepartures).keepingSelection() }
        }
    }

    /** A selection whose date is gone or can no longer be booked is dropped, never silently kept. */
    private fun BookingUiState.keepingSelection(): BookingUiState {
        val selected = selectedDepartureId ?: return this
        // Until a list with dates has answered there is nothing to check the selection against (and a
        // selection that matches no date selects nothing: see `selectedDeparture`).
        if (departures.isEmpty()) return this
        val stillOpen = departures.any { it.id == selected && it.isOpen }
        return if (stillOpen) this else copy(selectedDepartureId = null)
    }

    /** A party chosen before the policy arrived may be over its limit. */
    private fun BookingUiState.withValidParty(): BookingUiState =
        copy(partySize = partySize.coerceIn(BookingUiState.MIN_PARTY_SIZE, maxPartySize))

    private fun AppError.isStaleDates(): Boolean =
        this is AppError.Api && (code == ApiErrorCodes.NO_SEATS_AVAILABLE || code == ApiErrorCodes.DEPARTURE_NOT_OPEN)
}
