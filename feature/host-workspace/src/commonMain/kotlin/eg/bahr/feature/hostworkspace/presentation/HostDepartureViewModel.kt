package eg.bahr.feature.hostworkspace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.phone.PhoneNumbers
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.ApiErrorReasons
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.hostworkspace.data.HostRefusal
import eg.bahr.feature.hostworkspace.data.HostWorkspaceRepository
import eg.bahr.feature.hostworkspace.data.ManualBooking
import eg.bahr.feature.hostworkspace.data.hostRefusal
import eg.bahr.feature.hostworkspace.data.outcomeUnclear
import eg.bahr.feature.hostworkspace.model.HostDepartureDto
import eg.bahr.feature.hostworkspace.model.HostDepartureStatus
import eg.bahr.feature.hostworkspace.model.HostTravellerDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The manifest of one date, or why it is not shown. */
internal sealed interface ManifestLoad {
    data object Loading : ManifestLoad

    data class Loaded(
        val departure: HostDepartureDto,
    ) : ManifestLoad

    /** FORBIDDEN or NOT_FOUND alike (M5-B5 review #8): another host's date or none at all; nothing says which. */
    data object NotAvailable : ManifestLoad

    data object Suspended : ManifestLoad

    data class Failed(
        val error: AppError,
    ) : ManifestLoad
}

/** A line over the list after something happened. */
internal sealed interface ManifestNotice {
    /** A manual booking may have gone through without the app hearing so: check the refreshed list before adding again. */
    data object CheckList : ManifestNotice

    data class BroadcastSent(
        val recipients: Int,
        val nextAllowedAt: Instant?,
    ) : ManifestNotice

    data class BookingAdded(
        val name: String,
    ) : ManifestNotice

    data class MarkedPaid(
        val name: String,
    ) : ManifestNotice
}

internal enum class BroadcastStep { Compose, Confirm }

internal sealed interface BroadcastProblem {
    data object Required : BroadcastProblem

    data object TooLong : BroadcastProblem

    /** RATE_LIMITED: [at] from `retryAfter` (null when the server gave none). */
    data class TooSoon(
        val at: Instant?,
    ) : BroadcastProblem

    data object NoRecipients : BroadcastProblem

    data object NotOpen : BroadcastProblem

    /** VALIDATION_FAILED from the server: the message was refused as it is. */
    data object Rejected : BroadcastProblem

    data class Other(
        val error: AppError,
    ) : BroadcastProblem
}

internal data class BroadcastForm(
    val message: String = "",
    val step: BroadcastStep = BroadcastStep.Compose,
    val sending: Boolean = false,
    val problem: BroadcastProblem? = null,
)

internal enum class ManualStep { Edit, Review }

internal sealed interface ManualProblem {
    data object NoSeats : ManualProblem

    data object NotOpen : ManualProblem

    /** The server refused a field (VALIDATION_FAILED): nothing recorded. */
    data object Rejected : ManualProblem

    /** No clear answer: the booking may be recorded; pressing Add again with the same key never adds it twice. */
    data object RetrySafe : ManualProblem

    /** The key was used with other details: nothing new added; the list was refreshed, Add is gone. */
    data object KeyReused : ManualProblem

    data class Other(
        val error: AppError,
    ) : ManualProblem
}

internal data class ManualForm(
    val name: String = "",
    val phone: String = "",
    val partySize: Int = 1,
    val paid: Boolean = false,
    /** The guest's language for messages about the booking (openapi `locale`), the app's by default. */
    val locale: String,
    val step: ManualStep = ManualStep.Edit,
    val checked: Boolean = false,
    val sending: Boolean = false,
    val problem: ManualProblem? = null,
    /** An attempt of this opening ended without a clear answer: closing the form asks to check the list. */
    val uncertain: Boolean = false,
) {
    val normalizedPhone: String get() = PhoneNumbers.normalize(phone)
    val nameMissing: Boolean get() = name.isBlank()
    val nameTooLong: Boolean get() = name.trim().length > NAME_MAX
    val phoneInvalid: Boolean get() = !PhoneNumbers.isValid(normalizedPhone)
    val valid: Boolean get() = !nameMissing && !nameTooLong && !phoneInvalid && partySize >= 1

    /** After IDEMPOTENCY_KEY_REUSED this opening's key may not add anything: only "Check the list" is offered. */
    val canAdd: Boolean get() = problem != ManualProblem.KeyReused

    companion object {
        /** openapi `createManualBooking.name.maxLength`. */
        const val NAME_MAX = 80

        /**
         * A UI bound only (M5-W1 review #5): the contract sets no maximum and the date's free seats decide; past this a
         * stepper is the wrong tool, and the server still refuses more than are free.
         */
        const val PARTY_MAX = 99
    }
}

internal data class MarkPaidForm(
    val bookingId: String,
    val name: String,
    val ref: String,
    val sending: Boolean = false,
    val error: AppError? = null,
)

internal data class HostDepartureUiState(
    val load: ManifestLoad = ManifestLoad.Loading,
    val refreshing: Boolean = false,
    val notice: ManifestNotice? = null,
    /** Until when the date takes no broadcast (its `nextAllowedAt`, or a refusal's `retryAfter`); null when it does. */
    val broadcastLockedUntil: Instant? = null,
    val broadcast: BroadcastForm? = null,
    val manual: ManualForm? = null,
    val markPaid: MarkPaidForm? = null,
    /** Host mode's sign-in ended (a 401 nothing could cure, a sign-out elsewhere): the screen goes back. */
    val signedOut: Boolean = false,
) {
    val departure: HostDepartureDto? get() = (load as? ManifestLoad.Loaded)?.departure
    val open: Boolean get() = departure?.status == HostDepartureStatus.OPEN
}

/**
 * One of the host's dates (M5-M1): the manifest (`getHostDeparture`), the broadcast with its confirm step, a manual
 * booking, and marking one paid. The rules of M5-B2/B3 and the web's review (M5-W1):
 *
 * - **Manual booking:** one `Idempotency-Key` per opening of the form, sent again on every Add of that opening (an
 *   edit included: a refused request records nothing, so its key is free; an unclear one must keep it so a lost answer
 *   is not entered twice). Never retried without the host pressing Add. After any attempt without a clear answer, and
 *   after IDEMPOTENCY_KEY_REUSED, the manifest is read again and the host asked to check it before adding again; after
 *   IDEMPOTENCY_KEY_REUSED Add is gone for this opening.
 * - **Broadcast:** written, then confirmed, then sent; refused until the date's `nextAllowedAt` (device clock: the host
 *   answers carry no `serverNow`) and after a RATE_LIMITED for its `retryAfter`; the manifest is read again after any
 *   failure, so the lock and the last broadcast are the server's.
 */
internal class HostDepartureViewModel(
    private val departureId: String,
    private val repository: HostWorkspaceRepository,
    private val clock: Clock = Clock.System,
    private val newKey: () -> String = ::randomKey,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HostDepartureUiState())
    val uiState: StateFlow<HostDepartureUiState> = _uiState.asStateFlow()

    private var manualKey: String? = null
    private var loading: Job? = null
    private var unlock: Job? = null

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                if (session is SessionState.SignedOut) _uiState.update { it.copy(signedOut = true) }
            }
        }
        reload()
    }

    /** Reads the manifest again (first open, a return to the page, "Try again", after every change or refusal). */
    fun reload() {
        loading?.cancel()
        _uiState.update { it.copy(refreshing = it.load is ManifestLoad.Loaded) }
        loading =
            viewModelScope.launch {
                when (val result = repository.departure(departureId)) {
                    is AppResult.Success -> show(result.data)
                    is AppResult.Failure -> {
                        val load =
                            when (result.error.hostRefusal()) {
                                HostRefusal.Suspended -> ManifestLoad.Suspended
                                HostRefusal.NotAvailable -> ManifestLoad.NotAvailable
                                // A failed re-read keeps the list on screen rather than blanking it.
                                HostRefusal.Other ->
                                    _uiState.value.load.takeIf { it is ManifestLoad.Loaded }
                                        ?: ManifestLoad.Failed(result.error)
                            }
                        _uiState.update { it.copy(load = load, refreshing = false) }
                    }
                }
            }
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    // ---------- broadcast ----------

    fun openBroadcast() {
        val state = _uiState.value
        if (!state.open || state.broadcastLocked()) return
        _uiState.update { it.copy(broadcast = BroadcastForm()) }
    }

    fun onBroadcastMessage(value: String) = updateBroadcast { it.copy(message = value, problem = null) }

    /** Compose → Confirm, once the message is fit to send. */
    fun reviewBroadcast() =
        updateBroadcast { form ->
            val text = form.message.trim()
            when {
                text.isEmpty() -> form.copy(problem = BroadcastProblem.Required)
                text.length > BROADCAST_MAX -> form.copy(problem = BroadcastProblem.TooLong)
                else -> form.copy(step = BroadcastStep.Confirm, problem = null)
            }
        }

    fun editBroadcast() = updateBroadcast { it.copy(step = BroadcastStep.Compose, problem = null) }

    fun closeBroadcast() {
        if (_uiState.value.broadcast?.sending == true) return
        _uiState.update { it.copy(broadcast = null) }
    }

    fun sendBroadcast() {
        val form = _uiState.value.broadcast ?: return
        if (form.sending || form.step != BroadcastStep.Confirm) return
        updateBroadcast { it.copy(sending = true, problem = null) }
        viewModelScope.launch {
            when (val result = repository.broadcast(departureId, form.message.trim())) {
                is AppResult.Success -> {
                    val next = parseInstant(result.data.nextAllowedAt)
                    _uiState.update {
                        it.copy(broadcast = null, notice = ManifestNotice.BroadcastSent(result.data.recipients, next))
                    }
                    lockBroadcastUntil(next)
                    reload()
                }
                is AppResult.Failure -> {
                    val problem = result.error.broadcastProblem()
                    if (problem is BroadcastProblem.TooSoon) lockBroadcastUntil(problem.at)
                    updateBroadcast { it.copy(sending = false, problem = problem) }
                    reload()
                }
            }
        }
    }

    // ---------- manual booking ----------

    /** Opens the form with a new key: this opening's, until the form closes. [locale] is the app's language. */
    fun openManualBooking(locale: String) {
        if (!_uiState.value.open) return
        manualKey = newKey()
        _uiState.update { it.copy(manual = ManualForm(locale = locale)) }
    }

    fun onManualName(value: String) = editManualField { it.copy(name = value.take(NAME_INPUT_MAX)) }

    fun onManualPhone(value: String) = editManualField { it.copy(phone = value.take(PHONE_INPUT_MAX)) }

    fun onManualParty(value: Int) = editManualField { it.copy(partySize = value.coerceIn(1, ManualForm.PARTY_MAX)) }

    fun onManualPaid(value: Boolean) = editManualField { it.copy(paid = value) }

    fun onManualLocale(value: String) = editManualField { it.copy(locale = value) }

    /** A field changed: an earlier refusal no longer applies, except a reused key, which holds for the whole opening. */
    private fun editManualField(change: (ManualForm) -> ManualForm) =
        updateManual { form ->
            if (form.sending) form else change(form).copy(problem = form.problem.takeIf { it == ManualProblem.KeyReused })
        }

    /** Edit → Review, once every field is fit. */
    fun reviewManual() =
        updateManual { form -> if (form.valid) form.copy(step = ManualStep.Review, checked = true) else form.copy(checked = true) }

    fun editManual() = updateManual { it.copy(step = ManualStep.Edit) }

    /** Closes the form; an attempt without a clear answer leaves the "check the list" line over the refreshed list. */
    fun closeManual() {
        val form = _uiState.value.manual ?: return
        if (form.sending) return
        manualKey = null
        _uiState.update { it.copy(manual = null, notice = if (form.uncertain) ManifestNotice.CheckList else it.notice) }
        if (form.uncertain) reload()
    }

    fun addManualBooking() {
        val form = _uiState.value.manual ?: return
        val key = manualKey ?: return
        if (form.sending || !form.canAdd || !form.valid) return
        updateManual { it.copy(sending = true, problem = null) }
        val booking =
            ManualBooking(
                name = form.name.trim(),
                phone = form.normalizedPhone,
                partySize = form.partySize,
                paid = form.paid,
                locale = form.locale,
            )
        viewModelScope.launch {
            when (val result = repository.createManualBooking(departureId, key, booking)) {
                is AppResult.Success -> {
                    manualKey = null
                    _uiState.update { it.copy(manual = null, notice = ManifestNotice.BookingAdded(booking.name)) }
                    show(result.data)
                }
                is AppResult.Failure -> {
                    val error = result.error
                    val unclear = error.outcomeUnclear()
                    updateManual { it.copy(sending = false, problem = error.manualProblem(), uncertain = it.uncertain || unclear) }
                    // Every failure reads the list again: seats, the date's status, and a booking that went through after all.
                    reload()
                }
            }
        }
    }

    // ---------- mark paid ----------

    fun openMarkPaid(traveller: HostTravellerDto) {
        val id = traveller.id ?: return
        if (!traveller.manual || traveller.paid || !_uiState.value.open) return
        _uiState.update { it.copy(markPaid = MarkPaidForm(bookingId = id, name = traveller.name, ref = traveller.ref)) }
    }

    fun closeMarkPaid() {
        if (_uiState.value.markPaid?.sending == true) return
        _uiState.update { it.copy(markPaid = null) }
    }

    fun confirmMarkPaid() {
        val form = _uiState.value.markPaid ?: return
        if (form.sending) return
        _uiState.update { it.copy(markPaid = form.copy(sending = true, error = null)) }
        viewModelScope.launch {
            when (val result = repository.markPaid(departureId, form.bookingId)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(markPaid = null, notice = ManifestNotice.MarkedPaid(form.name)) }
                    show(result.data)
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(markPaid = it.markPaid?.copy(sending = false, error = result.error)) }
                    reload()
                }
            }
        }
    }

    // ---------- internals ----------

    private fun show(departure: HostDepartureDto) {
        _uiState.update { it.copy(load = ManifestLoad.Loaded(departure), refreshing = false) }
        lockBroadcastUntil(departure.broadcast?.nextAllowedAt?.let(::parseInstant))
    }

    /** Locks the broadcast until [until] (the later of what is known), and unlocks it by itself when that time comes. */
    private fun lockBroadcastUntil(until: Instant?) {
        val now = clock.now()
        val current = _uiState.value.broadcastLockedUntil?.takeIf { it > now }
        val next = listOfNotNull(current, until?.takeIf { it > now }).maxOrNull()
        _uiState.update { it.copy(broadcastLockedUntil = next) }
        unlock?.cancel()
        if (next != null) {
            unlock =
                viewModelScope.launch {
                    delay(next - clock.now())
                    _uiState.update { if (it.broadcastLockedUntil == next) it.copy(broadcastLockedUntil = null) else it }
                }
        }
    }

    private fun HostDepartureUiState.broadcastLocked(): Boolean = broadcastLockedUntil?.let { it > clock.now() } == true

    private fun updateBroadcast(change: (BroadcastForm) -> BroadcastForm) =
        _uiState.update { state ->
            state.broadcast?.let { state.copy(broadcast = change(it)) }
                ?: state
        }

    private fun updateManual(change: (ManualForm) -> ManualForm) =
        _uiState.update { state ->
            state.manual?.let { state.copy(manual = change(it)) }
                ?: state
        }

    private fun AppError.broadcastProblem(): BroadcastProblem {
        val api = this as? AppError.Api ?: return BroadcastProblem.Other(this)
        return when {
            api.code == ApiErrorCodes.RATE_LIMITED -> BroadcastProblem.TooSoon(api.retryAfterSeconds?.let { clock.now() + it.seconds })
            api.code == ApiErrorCodes.CONFLICT && api.reason == ApiErrorReasons.NO_RECIPIENTS -> BroadcastProblem.NoRecipients
            api.code == ApiErrorCodes.DEPARTURE_NOT_OPEN -> BroadcastProblem.NotOpen
            api.code == ApiErrorCodes.VALIDATION_FAILED -> BroadcastProblem.Rejected
            else -> BroadcastProblem.Other(this)
        }
    }

    private fun AppError.manualProblem(): ManualProblem {
        val api = this as? AppError.Api
        return when {
            api?.reason == ApiErrorReasons.IDEMPOTENCY_KEY_REUSED -> ManualProblem.KeyReused
            outcomeUnclear() -> ManualProblem.RetrySafe
            api?.code == ApiErrorCodes.NO_SEATS_AVAILABLE -> ManualProblem.NoSeats
            api?.code == ApiErrorCodes.DEPARTURE_NOT_OPEN -> ManualProblem.NotOpen
            api?.code == ApiErrorCodes.VALIDATION_FAILED -> ManualProblem.Rejected
            else -> ManualProblem.Other(this)
        }
    }

    companion object {
        /** openapi `broadcastToDeparture.message.maxLength` (the server's `max-length`, 600 by default). */
        const val BROADCAST_MAX = 600

        /** Typing past the contract's 80 still shows (and the field says "too long") rather than being cut silently. */
        private const val NAME_INPUT_MAX = ManualForm.NAME_MAX * 2

        /** Room for spaces and dashes around the 16 a phone may have. */
        private const val PHONE_INPUT_MAX = 32
    }
}

/** A random UUID (the platform's secure generator): one per manual booking the host means to enter. */
@OptIn(ExperimentalUuidApi::class)
internal fun randomKey(): String = Uuid.random().toString()

/** An offset date-time from the server, or null when it does not parse (shown without that line, never a crash). */
internal fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
