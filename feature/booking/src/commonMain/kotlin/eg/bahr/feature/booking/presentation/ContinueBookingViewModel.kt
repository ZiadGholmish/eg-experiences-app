package eg.bahr.feature.booking.presentation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.ActiveHoldStore
import eg.bahr.core.datastore.StoredHold
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Home's "Continue your booking" card. Shown only while [booking] is set: the server has said the
 * stored hold is still held. What a tap opens is built at the tap ([ContinueBookingViewModel.routeForTap]).
 */
internal data class ContinueBookingUiState(
    /** The fresh `GET /bookings/{ref}`: the trip title, date and party on the card. */
    val booking: HeldBookingDto? = null,
    /** Seconds left, rounded up; drives `mm:ss`. */
    val secondsLeft: Int = 0,
    /** Share of the hold still left, 1 → 0, as on the hold screen. */
    val progress: Float = 1f,
) {
    val isVisible: Boolean get() = booking != null
}

/**
 * The live hold this device placed, if any, for the card at the top of Home (PLAN M2-M4).
 *
 * Nothing is shown from storage alone: every time Home starts ([onStart]) the stored hold is read
 * again with `GET /bookings/{ref}`, and the card appears only while the server says HELD or
 * PAYMENT_PENDING. Any other status, or NOT_FOUND (an unknown ref or a phone that does not match),
 * forgets the stored hold. No answer leaves it stored and hidden, retried with backoff: the seats are
 * never assumed gone, nor shown as held on a guess.
 *
 * The countdown is the hold screen's: deadline-driven from the server's `holdExpiresAt` corrected
 * by its `serverNow` ([HoldDeadline]), ticked every [HoldViewModel.TICK] against [clock]; at zero the
 * booking is read again.
 */
internal class ContinueBookingViewModel(
    private val activeHold: ActiveHoldStore,
    private val repository: BookingRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ContinueBookingUiState())
    val uiState: StateFlow<ContinueBookingUiState> = _uiState.asStateFlow()

    private var deadline: HoldDeadline? = null
    private var holdLength: Duration? = null

    /** The last answer that showed the card, and when it arrived on the device's clock. */
    private var shown: Shown? = null

    private class Shown(
        val stored: StoredHold,
        val booking: HeldBookingDto,
        /** The live deadline exactly as the server sent it (`liveDeadline` has parsed it). */
        val holdExpiresAt: String,
        val serverNow: Instant,
        val receivedAt: Instant,
    )

    /** The host screen's lifecycle, once [attach]ed; observed until this view model is cleared. */
    private var lifecycle: Lifecycle? = null
    private val lifecycleObserver =
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onStart()
                Lifecycle.Event.ON_STOP -> onStop()
                else -> Unit
            }
        }
    private var ticker: Job? = null
    private var reread: Job? = null

    /** Set while a re-read is owed (the countdown hit zero, or the last read got no answer). */
    private var nextCheckAt: Instant? = null
    private var emptyChecks = 0

    /** Between [onStart] and [onStop]; a read answering after Home was hidden must not start ticking. */
    private var started = false

    /**
     * Follows the host screen's lifecycle (Home's back-stack entry, which this view model lives as
     * long as) rather than the card's place in composition: the card sits in a lazy list's first
     * item, and scrolling it out of view and back must not read the booking again. Idempotent.
     */
    fun attach(hostLifecycle: Lifecycle) {
        if (lifecycle === hostLifecycle) return
        lifecycle?.removeObserver(lifecycleObserver)
        lifecycle = hostLifecycle
        // Replays the current state: attached to a started screen, this starts at once.
        hostLifecycle.addObserver(lifecycleObserver)
    }

    override fun onCleared() {
        lifecycle?.removeObserver(lifecycleObserver)
        lifecycle = null
    }

    /** Home is visible: read the stored hold again and, while it shows, tick. */
    fun onStart() {
        started = true
        nextCheckAt = null
        emptyChecks = 0
        // A card already showing counts on at once: its deadline is anchored to the server's clock,
        // so it is still right after Home was hidden, and the read below only corrects it. Waiting
        // for that read (up to the request timeout on a bad network) would leave the time from when
        // Home was hidden on screen, and a tap would open the hold screen with that much too long.
        if (shown != null) {
            refreshClock(clock.now())
            ensureTicking()
        }
        reread()
    }

    /**
     * What a tap opens: the stored hold with the server's clock as of now (its last answer plus the
     * time since), so the hold screen's first deadline is as right as the card's, however long ago
     * the card read the booking. Built here rather than on every tick, which would change the state
     * twice a second for a value only a tap needs. Null while the card is hidden.
     */
    fun routeForTap(): HoldRoute? {
        val last = shown ?: return null
        val serverNow = last.serverNow + (clock.now() - last.receivedAt)
        return reopenedHold(last.stored, last.booking, last.holdExpiresAt, serverNow = serverNow.toString())
    }

    /** Home is hidden: stop ticking (the deadline does not move, so nothing is lost). */
    fun onStop() {
        started = false
        ticker?.cancel()
        ticker = null
    }

    private fun reread() {
        if (reread?.isActive == true) return
        reread =
            viewModelScope.launch {
                val stored = activeHold.hold.first()
                if (stored == null) {
                    hide()
                    return@launch
                }
                when (val result = repository.heldBooking(stored.ref, stored.guestPhone)) {
                    is AppResult.Success -> onBookingRead(stored, result.data)
                    is AppResult.Failure ->
                        if (result.error.isNotFound()) {
                            activeHold.clear(stored.ref)
                            hide()
                        } else {
                            // Whatever shows stays (a live countdown keeps counting); ask again later.
                            scheduleNextCheck()
                        }
                }
            }
    }

    private suspend fun onBookingRead(
        stored: StoredHold,
        booking: HeldBookingDto,
    ) {
        val receivedAt = clock.now()
        val fresh = booking.liveDeadline(receivedAt)
        val serverNow = HoldDeadline.parseInstant(booking.serverNow)
        val holdExpiresAt = booking.holdExpiresAt
        if (fresh == null || serverNow == null || holdExpiresAt == null) {
            activeHold.clear(stored.ref)
            hide()
            return
        }
        deadline = fresh
        // The placement's pair is the hold's full length, as on the hold screen's progress bar.
        holdLength = HoldDeadline.length(stored.holdExpiresAt, stored.serverNow)
        shown = Shown(stored, booking, holdExpiresAt, serverNow, receivedAt)
        _uiState.update { it.copy(booking = booking) }
        refreshClock(receivedAt)
        if (fresh.remaining(receivedAt) > Duration.ZERO) {
            emptyChecks = 0
            nextCheckAt = null
        } else {
            // Held, but with nothing left: the server is about to call it EXPIRED.
            scheduleNextCheck()
        }
        ensureTicking()
    }

    private fun ensureTicking() {
        if (!started || ticker?.isActive == true) return
        ticker =
            viewModelScope.launch {
                while (isActive) {
                    tick()
                    delay(HoldViewModel.TICK)
                }
            }
    }

    private fun tick() {
        val now = clock.now()
        if (_uiState.value.isVisible) refreshClock(now)
        val owed = nextCheckAt
        when {
            owed != null -> if (now >= owed && reread?.isActive != true) reread()
            _uiState.value.isVisible && _uiState.value.secondsLeft == 0 -> {
                // At zero nothing is assumed: the booking is read again.
                emptyChecks = 0
                nextCheckAt = now
                reread()
            }
        }
    }

    private fun refreshClock(now: Instant) {
        val left = deadline?.remaining(now) ?: Duration.ZERO
        val progress = holdLength?.let { (left / it).toFloat().coerceIn(0f, 1f) } ?: 0f
        _uiState.update { it.copy(secondsLeft = left.secondsLeftCeil(), progress = progress) }
    }

    private fun scheduleNextCheck() {
        emptyChecks++
        nextCheckAt = clock.now() + recheckBackoff(emptyChecks)
        ensureTicking()
    }

    private fun hide() {
        ticker?.cancel()
        ticker = null
        deadline = null
        shown = null
        nextCheckAt = null
        _uiState.value = ContinueBookingUiState()
    }
}
