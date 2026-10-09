package eg.bahr.feature.booking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Where the hold stands, as far as this screen knows. */
internal enum class HoldPhase {
    /** Counting down against the server's deadline. */
    Holding,

    /** The countdown reached zero; the booking is being re-read, never assumed gone. */
    Checking,

    /** The server says the seats are no longer held (expired, or released elsewhere). */
    Expired,

    /** The user backed out and the server released the seats. */
    Released,

    /** The user backed out but the release did not get through; the server frees the seats at the deadline. */
    LeftUnreleased,
    ;

    /** Phases after which the screen leaves. */
    val isEnded: Boolean get() = this == Expired || this == Released || this == LeftUnreleased
}

internal data class HoldUiState(
    val ref: String,
    val totalAmount: Long,
    val totalCurrency: String,
    val phase: HoldPhase = HoldPhase.Holding,
    /** Seconds left, rounded up; drives `mm:ss`. */
    val secondsLeft: Int = 0,
    /** Minutes left, rounded up; only this is announced, so a screen reader speaks once a minute. */
    val minutesLeft: Int = 0,
    /** Share of the hold still left, 1 → 0, against the hold's length on the server's clock. */
    val progress: Float = 1f,
    /** The re-read booking: the trip summary. Null until `GET /bookings/{ref}` first answers. */
    val booking: HeldBookingDto? = null,
    /** The last re-read at zero failed; shown under the countdown while it retries. */
    val checkError: AppError? = null,
    val isLeaveConfirmVisible: Boolean = false,
    val isReleasing: Boolean = false,
    /** The release failed; the dialog says so and offers to leave anyway. */
    val releaseError: AppError? = null,
    /** The screen has passed the end on ([HoldViewModel.onEndReported]); it must not pass it on twice. */
    val isEndReported: Boolean = false,
)

/**
 * The held seats (HANDOFF screen 5, "Payment", step 2 of 3) without payment, which is M3.
 *
 * The countdown is deadline-driven: each tick reads [clock] against a [HoldDeadline] built from the
 * server's `holdExpiresAt` and `serverNow`, never a counter decremented per tick, so a late tick or a
 * backgrounded app cannot make it drift. The deadline is re-built from a fresh `GET /bookings/{ref}`
 * every time the screen starts ([onStart]: first open, back from the background, back from a trip
 * opened over it by a link, restored after process death) and when it reaches zero.
 *
 * At zero nothing is assumed: the booking is re-read. Still held with time left (the device clock
 * ran fast) → keep counting; anything else → [HoldPhase.Expired]; no answer → retry with backoff.
 */
internal class HoldViewModel(
    private val hold: HoldRoute,
    private val repository: BookingRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    /** The hold's full length by the server's clock, from the answer that placed it; never re-derived. */
    private val holdLength: Duration? = HoldDeadline.length(hold.holdExpiresAt, hold.serverNow)

    /**
     * Until the first re-read answers, the route's own answer stands in. It is fresh on a normal
     * open; after process death it is stale (too generous), which the re-read on start corrects.
     */
    private var deadline: HoldDeadline? = HoldDeadline.from(hold.holdExpiresAt, hold.serverNow, clock.now())

    private var ticker: Job? = null
    private var reread: Job? = null

    /** When to re-read next while [HoldPhase.Checking], and how many re-reads have come back empty. */
    private var nextCheckAt: Instant? = null
    private var emptyChecks = 0

    private val _uiState =
        MutableStateFlow(HoldUiState(ref = hold.ref, totalAmount = hold.totalAmount, totalCurrency = hold.totalCurrency))
    val uiState: StateFlow<HoldUiState> = _uiState.asStateFlow()

    init {
        refreshClock()
    }

    /** The screen is visible: re-sync from the server and tick. */
    fun onStart() {
        if (_uiState.value.phase.isEnded) return
        reread()
        if (ticker?.isActive != true) {
            ticker =
                viewModelScope.launch {
                    while (isActive) {
                        tick()
                        delay(TICK)
                    }
                }
        }
    }

    /** The screen is hidden: stop ticking (the deadline does not move, so nothing is lost). */
    fun onStop() {
        ticker?.cancel()
        ticker = null
    }

    /** The screen has handed the end of the hold to navigation. */
    fun onEndReported() = _uiState.update { it.copy(isEndReported = true) }

    /** Back (top bar or system): confirm before the seats are given up. */
    fun requestLeave() {
        if (_uiState.value.phase.isEnded) return
        _uiState.update { it.copy(isLeaveConfirmVisible = true, releaseError = null) }
    }

    fun dismissLeave() {
        if (_uiState.value.isReleasing) return
        _uiState.update { it.copy(isLeaveConfirmVisible = false, releaseError = null) }
    }

    /**
     * Release the seats and leave. `releaseHold` is idempotent (204 also when already expired or
     * released), so a release racing the deadline is safe. If it fails, the dialog says so and
     * offers [leaveWithoutRelease].
     */
    fun confirmLeave() {
        val state = _uiState.value
        if (state.isReleasing || state.phase.isEnded) return
        _uiState.update { it.copy(isReleasing = true, releaseError = null) }
        viewModelScope.launch {
            when (val result = repository.releaseHold(hold.ref, hold.guestPhone)) {
                is AppResult.Success -> {
                    stopAll()
                    _uiState.update { it.copy(isReleasing = false, isLeaveConfirmVisible = false, phase = HoldPhase.Released) }
                }
                is AppResult.Failure ->
                    _uiState.update { it.copy(isReleasing = false, releaseError = result.error) }
            }
        }
    }

    /**
     * Leave after a failed release. The server frees the seats at the deadline on its own (the
     * sweeper), so the only cost is that they stay off sale until then.
     */
    fun leaveWithoutRelease() {
        if (_uiState.value.isReleasing) return
        stopAll()
        _uiState.update { it.copy(isLeaveConfirmVisible = false, phase = HoldPhase.LeftUnreleased) }
    }

    private fun tick() {
        val now = clock.now()
        refreshClock(now)
        val state = _uiState.value
        when {
            state.phase == HoldPhase.Holding && state.secondsLeft == 0 -> {
                _uiState.update { it.copy(phase = HoldPhase.Checking) }
                nextCheckAt = now
                emptyChecks = 0
                reread()
            }
            state.phase == HoldPhase.Checking && reread?.isActive != true && now >= (nextCheckAt ?: now) -> reread()
        }
    }

    private fun refreshClock(now: Instant = clock.now()) {
        val left = deadline?.remaining(now) ?: Duration.ZERO
        val progress = holdLength?.let { (left / it).toFloat().coerceIn(0f, 1f) } ?: 0f
        _uiState.update { it.copy(secondsLeft = left.secondsLeftCeil(), minutesLeft = left.minutesLeftCeil(), progress = progress) }
    }

    /** `GET /bookings/{ref}`: the fresh deadline, or the news that the seats are gone. */
    private fun reread() {
        if (reread?.isActive == true) return
        reread =
            viewModelScope.launch {
                when (val result = repository.heldBooking(hold.ref, hold.guestPhone)) {
                    is AppResult.Success -> onBookingRead(result.data)
                    is AppResult.Failure -> {
                        // While still counting, a failed re-read changes nothing: the deadline we
                        // have stands. At zero it is retried; the seats are never assumed gone.
                        if (_uiState.value.phase == HoldPhase.Checking) {
                            _uiState.update { it.copy(checkError = result.error) }
                            scheduleNextCheck()
                        }
                    }
                }
            }
    }

    private fun onBookingRead(booking: HeldBookingDto) {
        if (_uiState.value.phase.isEnded) return
        val receivedAt = clock.now()
        val fresh = HoldDeadline.from(booking.holdExpiresAt, booking.serverNow, receivedAt)
        val stillHeld = booking.status in HELD_STATUSES && fresh != null
        _uiState.update { it.copy(booking = booking, checkError = null) }
        when {
            !stillHeld -> {
                stopAll()
                _uiState.update { it.copy(phase = HoldPhase.Expired, isLeaveConfirmVisible = false) }
            }
            fresh.remaining(receivedAt) > Duration.ZERO -> {
                deadline = fresh
                emptyChecks = 0
                _uiState.update { it.copy(phase = HoldPhase.Holding) }
                refreshClock(receivedAt)
            }
            else -> {
                // Held, but with nothing left: the server is about to call it EXPIRED. Ask again
                // shortly rather than in a tight loop.
                deadline = fresh
                refreshClock(receivedAt)
                if (_uiState.value.phase == HoldPhase.Holding) _uiState.update { it.copy(phase = HoldPhase.Checking) }
                scheduleNextCheck()
            }
        }
    }

    private fun scheduleNextCheck() {
        emptyChecks++
        val backoff = (FIRST_RECHECK * (1 shl (emptyChecks - 1).coerceAtMost(MAX_BACKOFF_SHIFT))).coerceAtMost(MAX_RECHECK)
        nextCheckAt = clock.now() + backoff
    }

    private fun stopAll() {
        ticker?.cancel()
        ticker = null
        reread?.cancel()
    }

    companion object {
        /** HANDOFF: ticks every 500 ms, so the shown second is never more than half a second late. */
        val TICK = 500.milliseconds

        /** The first re-read after an empty or failed one at zero; doubled each time up to [MAX_RECHECK]. */
        val FIRST_RECHECK = 2.seconds
        val MAX_RECHECK = 30.seconds
        private const val MAX_BACKOFF_SHIFT = 4

        /** Statuses in which the seats are still held for this booking. */
        val HELD_STATUSES = setOf("HELD", "PAYMENT_PENDING")
    }
}
