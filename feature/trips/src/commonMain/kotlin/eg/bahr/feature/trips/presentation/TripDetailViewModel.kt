package eg.bahr.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.trips.data.JoinedWaitlist
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.WaitlistRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

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
    /** The waiting-list form under the sold-out notice; null until "Join the waiting list" is tapped. */
    val waitlist: WaitlistForm? = null,
    /**
     * This trip's dates the device is on the waiting list of, by departure id, with the phone they were
     * joined under (said back in the confirmation). Kept per date so another date never reads as
     * joined. Remembered on the device (M4-M5, `WaitlistMemory`), so it survives leaving the page and
     * a restart; a join made now shows at once, before the store has written it.
     */
    val joinedWaitlists: Map<String, String> = emptyMap(),
    /** What a refused join turned out to mean, said in the availability band (the notice may be gone). */
    val waitlistOutcome: WaitlistOutcome? = null,
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
            return if (selected.availability == DateAvailability.Open) TripCta.Continue else TripCta.SoldOut
        }

    /** The date "Continue" books. Null unless the selected date is bookable, so sold out never continues. */
    val continueDepartureId: String?
        get() = selectedDeparture?.takeIf { it.availability == DateAvailability.Open }?.id

    /**
     * Under a selected sold-out date, the date the notice points to instead: the next bookable one
     * after it, else any bookable one. Null when nothing has seats.
     */
    val alternative: DepartureDto?
        get() {
            val full = selectedDeparture?.takeIf { it.availability == DateAvailability.SoldOut } ?: return null
            val open = departures.filter { it.availability == DateAvailability.Open }
            return open.firstOrNull { it.date > full.date } ?: open.firstOrNull()
        }

    /** The party limit per booking, from the server's policy (`policy.maxPartySize`), else the handoff's 6. */
    val maxPartySize: Int
        get() = trip?.policy?.maxPartySize?.takeIf { it >= MIN_PARTY_SIZE } ?: FALLBACK_MAX_PARTY_SIZE

    /** The form for the selected date, if it is sold out and its form is open. */
    val openWaitlist: WaitlistForm?
        get() = waitlist?.takeIf { it.departureId == selectedDeparture?.takeIf { d -> d.availability == DateAvailability.SoldOut }?.id }

    /** The phone the selected date was joined under, if the device is on its waiting list. */
    val joinedPhone: String?
        get() = selectedDepartureId?.let { joinedWaitlists[it] }

    internal companion object {
        const val MIN_PARTY_SIZE = 1

        /** Only when the trip does not state `policy.maxPartySize`: the handoff's 6 (as on date + party). */
        const val FALLBACK_MAX_PARTY_SIZE = 6

        /** The longest value the contract's `Phone` pattern accepts: a `+` and 15 digits. */
        const val PHONE_MAX_LENGTH = 16

        /** openapi `Phone`. */
        val PHONE_PATTERN = Regex("""^\+?[0-9]{7,15}$""")
    }
}

/**
 * The waiting-list form for one sold-out date ([departureId]): the phone and party to put on its
 * list. [error] is a refusal the form can recover from (list full, bad details, no network).
 */
internal data class WaitlistForm(
    val departureId: String,
    val phone: String = "",
    val partySize: Int = TripDetailUiState.MIN_PARTY_SIZE,
    val submitting: Boolean = false,
    val error: AppError? = null,
) {
    /** The contract's `Phone` pattern, so a typo is caught before the request. */
    val isPhoneValid: Boolean
        get() = TripDetailUiState.PHONE_PATTERN.matches(phone)
}

/**
 * A join the server refused because the date is no longer what the page showed, after which the
 * dates are read again: [SeatsOpened] (`CONFLICT`: the date has seats, book it instead) or
 * [DateClosed] (`DEPARTURE_NOT_OPEN` / `NOT_FOUND`: cancelled, past its cutoff, or gone).
 */
internal data class WaitlistOutcome(
    val departureId: String,
    val date: LocalDate,
    val kind: Kind,
) {
    enum class Kind { SeatsOpened, DateClosed }
}

/**
 * A date can be picked when it can be booked, or when it is sold out: picking a full date shows the
 * sold-out notice (HANDOFF screen 3). A cancelled or closed date is shown with its reason on the card
 * but cannot be picked: there is no notice or waitlist for it (D3: only sold-out dates get one).
 */
internal fun DepartureDto.isSelectable(): Boolean =
    when (availability) {
        DateAvailability.Open, DateAvailability.SoldOut -> true
        DateAvailability.Cancelled, DateAvailability.Closed -> false
    }

/**
 * [waitlists] is the device's memory of waiting-list joins (M4-M5): read for this trip's dates, kept
 * in step with what the server's dates say (see [reconcileJoins]), and written on a successful join.
 */
internal class TripDetailViewModel(
    private val slug: String,
    private val repository: TripRepository,
    private val waitlists: WaitlistMemory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripDetailUiState(preview = repository.cachedCard(slug)))
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            waitlists.joins.collect { joins ->
                val today = waitlists.today()
                val mine = joins.filter { it.tripSlug == slug && it.date >= today }.associate { it.departureId to it.phone }
                _uiState.update { it.copy(joinedWaitlists = mine) }
            }
        }
        load()
    }

    /**
     * Two independent reads, each landing on its own: the trip renders as soon as it arrives, and
     * the date cards (live seat counts) fill in when theirs does. Once the dates are known, the
     * stored joins are checked against them, once per load.
     */
    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null, liveDeparturesLoading = true) }
        val tripRead =
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
            // The live list when it answered, else the trip's own copy once that has: both are every
            // upcoming date (the contract lists them all from today on). Neither: nothing is known, so
            // nothing is dropped.
            val dates = live ?: tripRead.join().let { _uiState.value.trip?.dates }
            if (dates != null) reconcileJoins(dates)
        }
    }

    /**
     * Keeps the stored joins of this trip in step with a fresh read of its [dates] (M4-M5):
     * - a date still sold out keeps its join;
     * - a date bookable again drops it, and the band says "Seats just opened up" for it, once: the
     *   join is gone, so the next visit says nothing;
     * - a date cancelled, closed (past its cutoff) or no longer listed drops it silently: there is no
     *   list left to be on (`joinWaitlist` answers DEPARTURE_NOT_OPEN / NOT_FOUND for it), and both
     *   reads list every upcoming date, so "not listed" means gone. Silent by product decision
     *   (Ziad, 2026-10-10): the date card already shows "Cancelled" / "Booking closed".
     * If two stored dates reopened at once, only the first gets the message (the band shows one
     * outcome); both joins are dropped.
     * Only this trip's joins are judged against its dates: another trip's are not in this list. Any
     * trip's passed dates are dropped too ([WaitlistMemory.forgetPassed]).
     */
    private suspend fun reconcileJoins(dates: List<DepartureDto>) {
        waitlists.forgetPassed()
        val byId = dates.associateBy { it.id }
        val mine = waitlists.joins.first().filter { it.tripSlug == slug }
        val reopened = mine.firstNotNullOfOrNull { join -> byId[join.departureId]?.takeIf { it.availability == DateAvailability.Open } }
        val dropped = mine.filterNot { byId[it.departureId]?.availability == DateAvailability.SoldOut }
        waitlists.forget(dropped.mapTo(mutableSetOf(), JoinedWaitlist::departureId))
        if (reopened != null) {
            _uiState.update {
                it.copy(waitlistOutcome = WaitlistOutcome(reopened.id, reopened.date, WaitlistOutcome.Kind.SeatsOpened))
            }
        }
    }

    /**
     * Tapping the selected date again clears it; a date that cannot be picked is ignored. A new pick
     * drops the waiting-list form and the last join's outcome: both were about the old date.
     */
    fun selectDeparture(departureId: String) {
        _uiState.update { state ->
            val departure = state.departures.firstOrNull { it.id == departureId }
            when {
                departure == null || !departure.isSelectable() -> state
                state.selectedDepartureId == departureId -> state.copy(selectedDepartureId = null).withoutWaitlistForm()
                else -> state.copy(selectedDepartureId = departureId).withoutWaitlistForm()
            }
        }
    }

    /** "Join the waiting list" on the sold-out notice: opens the form for the selected sold-out date. */
    fun openWaitlist() {
        _uiState.update { state ->
            val full = state.selectedDeparture?.takeIf { it.availability == DateAvailability.SoldOut } ?: return@update state
            when {
                // Already on its list: the notice says so instead of offering the form again.
                full.id in state.joinedWaitlists -> state
                state.waitlist?.departureId == full.id -> state
                else -> state.copy(waitlist = WaitlistForm(full.id))
            }
        }
    }

    /**
     * Keeps digits and a leading `+` only, capped at the longest number the contract's `Phone` pattern
     * accepts (as on date + party). The server compares phones by their digits.
     */
    fun setWaitlistPhone(phone: String) {
        val digits = phone.filter { it.isDigit() }
        val cleaned = (if (phone.trimStart().startsWith('+')) "+$digits" else digits).take(TripDetailUiState.PHONE_MAX_LENGTH)
        updateForm { it.copy(phone = cleaned, error = null) }
    }

    fun increaseWaitlistParty() {
        val max = _uiState.value.maxPartySize
        updateForm { it.copy(partySize = (it.partySize + 1).coerceAtMost(max), error = null) }
    }

    fun decreaseWaitlistParty() {
        updateForm { it.copy(partySize = (it.partySize - 1).coerceAtLeast(TripDetailUiState.MIN_PARTY_SIZE), error = null) }
    }

    /**
     * Sends the form. Branches on the error code only:
     * - `CONFLICT` (the date has seats again), `DEPARTURE_NOT_OPEN` / `NOT_FOUND` (cancelled, closed or
     *   gone): the date is no longer what the page showed, so the dates are read again and the band
     *   says what the fresh read shows ([outcomeAfterReread]);
     * - anything else (`RATE_LIMITED` = this date's list is full, `VALIDATION_FAILED`, network): stays
     *   on the form, which shows it.
     * Seat counts are hints, so a refusal is expected, never a crash.
     */
    fun joinWaitlist() {
        val state = _uiState.value
        val form = state.openWaitlist ?: return
        val full = state.selectedDeparture ?: return
        if (form.submitting || !form.isPhoneValid) return
        updateForm { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = repository.joinWaitlist(form.departureId, WaitlistRequest(phone = form.phone, partySize = form.partySize))
            when (result) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            joinedWaitlists = it.joinedWaitlists + (form.departureId to form.phone),
                            waitlist = it.waitlist?.takeUnless { open -> open.departureId == form.departureId },
                        )
                    }
                    waitlists.remember(form.departureId, slug, full.date, form.partySize, form.phone)
                }
                is AppResult.Failure ->
                    when ((result.error as? AppError.Api)?.code) {
                        ApiErrorCodes.CONFLICT, ApiErrorCodes.DEPARTURE_NOT_OPEN, ApiErrorCodes.NOT_FOUND ->
                            outcomeAfterReread(form.departureId, full.date, result.error)
                        else -> updateForm(form.departureId) { it.copy(submitting = false, error = result.error) }
                    }
            }
        }
    }

    /**
     * Reads the dates again after a refusal that says the date changed, and only then says how: the
     * band claims "seats opened up" only when the fresh read shows the date bookable, and "can't be
     * booked" only when it shows it cancelled, closed or gone. If the read fails, or still shows the
     * date sold out, nothing is claimed: the form stays, showing [refusal] (a neutral "try again" for
     * `CONFLICT`), and the dates already shown are kept rather than the trip's own, older copy.
     */
    private suspend fun outcomeAfterReread(
        departureId: String,
        date: LocalDate,
        refusal: AppError,
    ) {
        val live = (repository.departuresFor(slug) as? AppResult.Success)?.data
        val fresh = live?.firstOrNull { it.id == departureId }
        val kind =
            when {
                live == null -> null
                fresh == null -> WaitlistOutcome.Kind.DateClosed
                else ->
                    when (fresh.availability) {
                        DateAvailability.Open -> WaitlistOutcome.Kind.SeatsOpened
                        DateAvailability.Cancelled, DateAvailability.Closed -> WaitlistOutcome.Kind.DateClosed
                        DateAvailability.SoldOut -> null
                    }
            }
        _uiState.update { state ->
            val reread = if (live != null) state.copy(liveDepartures = live).keepingSelection() else state
            if (kind == null) {
                val form = reread.waitlist?.takeIf { it.departureId == departureId }
                reread.copy(waitlist = form?.copy(submitting = false, error = refusal) ?: reread.waitlist)
            } else {
                reread.copy(
                    waitlist = reread.waitlist?.takeUnless { it.departureId == departureId },
                    waitlistOutcome = WaitlistOutcome(departureId, date, kind),
                )
            }
        }
        // The date has seats or is gone: an older join for it (from another visit) means nothing now.
        if (kind != null) waitlists.forget(setOf(departureId))
    }

    /** Applies [change] to the open form, if it is still for [departureId] (the user may have moved on). */
    private fun updateForm(
        departureId: String? = null,
        change: (WaitlistForm) -> WaitlistForm,
    ) {
        _uiState.update { state ->
            val form = state.waitlist ?: return@update state
            if (departureId != null && form.departureId != departureId) state else state.copy(waitlist = change(form))
        }
    }

    private fun TripDetailUiState.withoutWaitlistForm() = copy(waitlist = null, waitlistOutcome = null)

    /** After a refresh, a selection whose date is gone (or no longer pickable) is dropped. */
    private fun TripDetailUiState.keepingSelection(): TripDetailUiState {
        val selected = selectedDepartureId ?: return this
        val stillThere = departures.any { it.id == selected && it.isSelectable() }
        return if (stillThere) this else copy(selectedDepartureId = null)
    }
}
