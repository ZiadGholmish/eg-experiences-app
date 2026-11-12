package eg.bahr.feature.booking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.ActiveHoldStore
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.di.CheckoutConfig
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.model.PayerRequest
import eg.bahr.feature.booking.model.PaymentStartedDto
import eg.bahr.feature.booking.model.StartPaymentRequest
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

    /**
     * The user backed out but the release did not get through, or a payment was under way (M3-M1), which a release
     * cannot undo: the server frees the seats at the deadline, or the payment confirms the booking.
     */
    LeftUnreleased,

    /** The server says the booking is paid (CONFIRMED): the screen hands over to the confirmation (M3-M1). */
    Paid,
    ;

    /** Phases after which the screen leaves. */
    val isEnded: Boolean get() = this == Expired || this == Released || this == LeftUnreleased || this == Paid
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
    /**
     * The methods offered, in order (MV-M4): the server's list ([HoldRoute.paymentMethods], then each HELD read), card
     * and Vodafone Cash when it sends none. Test cash only when the server lists it.
     */
    val methods: List<PaymentMethod> = PaymentMethod.DEFAULT_OFFERED,
    /** Checkout (M3-M1) from here on. Vodafone Cash by default (HANDOFF screen 5), always one of [methods]. */
    val method: PaymentMethod = PaymentMethod.VodafoneCash,
    val payer: PayerForm = PayerForm(),
    /** A pay tap found a field wrong: the fields say which from then on. */
    val showPayerErrors: Boolean = false,
    val payment: PaymentStep = PaymentStep.Choosing,
    /** Starting the payment failed (`PAYMENT_DECLINED`, no connection, …); the person may try again. */
    val paymentError: AppError? = null,
    /** The provider declined at its checkout: the booking read HELD again after a payment was started. */
    val declined: Boolean = false,
    /** The hosted checkout of the payment under way, to open again if its tab was closed before paying. */
    val checkoutUrl: String? = null,
    /** One-shot: the screen opens this in the browser, then calls [HoldViewModel.onCheckoutClosed]. */
    val checkoutToOpen: String? = null,
    /** The link the checkout returns to (this build's, under the booking's reference); the iOS session watches its scheme. */
    val checkoutReturnUrl: String? = null,
    /** One-shot: paying needs a sign-in; the screen opens the sign-in sheet, then [HoldViewModel.onSignInShown]. */
    val signInRequested: Boolean = false,
) {
    /** The pay button is live: seats still held with time left, and no payment started or starting. */
    val canPay: Boolean get() = phase == HoldPhase.Holding && secondsLeft > 0 && payment == PaymentStep.Choosing
}

/**
 * The held seats (HANDOFF screen 5, "Payment", step 2 of 3): the countdown, and since M3-M1 the checkout.
 *
 * The countdown is deadline-driven: each tick reads [clock] against a [HoldDeadline] built from the
 * server's `holdExpiresAt` and `serverNow`, never a counter decremented per tick, so a late tick or a
 * backgrounded app cannot make it drift. The deadline is re-built from a fresh `GET /bookings/{ref}`
 * every time the screen starts ([onStart]: first open, back from the background, back from a trip
 * opened over it by a link, restored after process death) and when it reaches zero.
 *
 * At zero nothing is assumed: the booking is re-read. Still held with time left (the device clock
 * ran fast) → keep counting; anything else → [HoldPhase.Expired]; no answer → retry with backoff.
 *
 * The device's stored hold ([activeHold], Home's "Continue your booking") is forgotten as soon as
 * the seats are released or the server says they are no longer held. Not when the user leaves after
 * a failed release ([leaveWithoutRelease]): the seats are still held then, and the Home card is the
 * way back to them.
 *
 * **Checkout (M3-M1).** [pay] checks the payer's fields, asks for a sign-in when nobody is signed in (paying needs a
 * customer's token; the sign-in sheet, no OTP), then `startPayment`s. From then on the booking is PAYMENT_PENDING and
 * the answer comes from polling `getBooking` (every [POLL] while the screen shows, and at once when it comes back from
 * the browser), never assumed:
 * - CONFIRMED (or COMPLETED) → [HoldPhase.Paid]: the stored hold and its reminder are forgotten.
 * - PAYMENT_PENDING → still waiting, whatever the countdown says: the server's grace (invisible) keeps the seats
 *   while the payment completes, so a past deadline then is not "time is up".
 * - HELD again → the provider declined it; the person may pay again before the deadline ([HoldUiState.declined]).
 * - EXPIRED → [PaymentStep.Late]: late money can still confirm it, or is refunded automatically; polled more slowly.
 * While a payment is under way Back cannot release the seats (`releaseHold` would answer CONFLICT): it only leaves.
 */
internal class HoldViewModel(
    private val hold: HoldRoute,
    private val repository: BookingRepository,
    private val activeHold: ActiveHoldStore,
    private val clock: Clock = Clock.System,
    /**
     * The "about to expire" reminder (M3-M7): moved to each fresh deadline, gone once the seats are released or no
     * longer held. Kept on [leaveWithoutRelease]: the seats are still held then.
     */
    private val reminders: HoldReminderSchedule = NoHoldReminderSchedule,
    /** Where the hosted checkout returns to (M3-M1); none by default. */
    private val checkout: CheckoutConfig = CheckoutConfig(),
) : ViewModel() {
    /** The hold's full length by the server's clock, from the answer that placed it; never re-derived. */
    private val holdLength: Duration? =
        hold.holdLengthSeconds?.seconds?.takeIf { it.isPositive() } ?: HoldDeadline.length(hold.holdExpiresAt, hold.serverNow)

    /**
     * Until the first re-read answers, the route's own answer stands in. It is fresh on a normal
     * open and on a reopen ([reopenedHold]: the caller has just read the booking); after process
     * death it is stale (too generous), which the re-read on start corrects.
     */
    private var deadline: HoldDeadline? = HoldDeadline.from(hold.holdExpiresAt, hold.serverNow, clock.now())

    private var ticker: Job? = null
    private var reread: Job? = null

    /** When to re-read next while [HoldPhase.Checking], and how many re-reads have come back empty. */
    private var nextCheckAt: Instant? = null
    private var emptyChecks = 0

    /** When to read the booking next while a payment is under way, and how many of those reads failed in a row. */
    private var nextPollAt: Instant? = null
    private var failedPolls = 0

    /**
     * Bumped as a payment starts and once it has started: a read launched before then may answer HELD from before the
     * start and would pass for a decline (M3-M1 review #1), so its answer is dropped.
     */
    private var paymentGeneration = 0

    private val _uiState =
        MutableStateFlow(
            HoldUiState(
                ref = hold.ref,
                totalAmount = hold.totalAmount,
                totalCurrency = hold.totalCurrency,
                payer = PayerForm.prefilled(hold.guestPhone),
                checkoutReturnUrl = checkout.returnUrlFor(hold.ref),
            ).withMethods(PaymentMethod.offered(hold.paymentMethods)),
        )
    val uiState: StateFlow<HoldUiState> = _uiState.asStateFlow()

    init {
        refreshClock()
        // The payer's name from the account once someone is signed in (M3-M1), unless one was typed already.
        viewModelScope.launch {
            repository.session.collect { session ->
                val name = (session as? SessionState.SignedIn)?.accountName?.takeIf { it.isNotBlank() } ?: return@collect
                _uiState.update { if (it.payer.name.isBlank()) it.copy(payer = it.payer.copy(name = name)) else it }
            }
        }
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

    /**
     * The reminder's pre-prompt was answered (M3-M7): read the hold again, so its reminder is scheduled afresh now that
     * notifications may be allowed (iOS may not keep a request added before the permission was granted).
     */
    fun onReminderPromptDone() {
        if (!_uiState.value.phase.isEnded) reread()
    }

    /** The screen has handed the end of the hold to navigation. */
    fun onEndReported() = _uiState.update { it.copy(isEndReported = true) }

    /** Back (top bar or system): confirm before the seats are given up. */
    fun requestLeave() {
        // While the payment starts there is nothing true to offer: releasing would race it (M3-M1 review #4). Its
        // answer comes in moments, and Back then offers what fits.
        if (_uiState.value.phase.isEnded || _uiState.value.payment == PaymentStep.Starting) return
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
        if (state.payment.isUnderWay) {
            // Nothing to release (it would answer CONFLICT): the payment confirms the booking or the seats free
            // themselves. The stored hold stays, so Home's card leads back to it.
            leaveWithoutRelease()
            return
        }
        _uiState.update { it.copy(isReleasing = true, releaseError = null) }
        viewModelScope.launch {
            when (val result = repository.releaseHold(hold.ref, hold.guestPhone)) {
                is AppResult.Success -> {
                    stopAll()
                    activeHold.clear(hold.ref)
                    reminders.onEnded(hold.ref)
                    _uiState.update { it.copy(isReleasing = false, isLeaveConfirmVisible = false, phase = HoldPhase.Released) }
                }
                is AppResult.Failure ->
                    if (result.error.isCode(ApiErrorCodes.CONFLICT)) {
                        // A payment started meanwhile (another device, or a late answer): "leave anyway" would be
                        // wrong (M2-M2 carried note). Read the booking: it shows the payment under way or done.
                        _uiState.update { it.copy(isReleasing = false, isLeaveConfirmVisible = false) }
                        reread()
                    } else {
                        _uiState.update { it.copy(isReleasing = false, releaseError = result.error) }
                    }
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
            // A payment under way is polled on its own rhythm; zero on the countdown means nothing then.
            state.payment.isUnderWay -> if (reread?.isActive != true && now >= (nextPollAt ?: now)) reread()
            // Its answer decides; the countdown waits for it.
            state.payment == PaymentStep.Starting -> Unit
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
        val generation = paymentGeneration
        reread =
            viewModelScope.launch {
                val result = repository.heldBooking(hold.ref, hold.guestPhone)
                // Sent before the payment started (or while it was starting): its HELD says nothing about the payment.
                if (generation != paymentGeneration) return@launch
                when (result) {
                    is AppResult.Success -> onBookingRead(result.data)
                    is AppResult.Failure -> {
                        // While still counting, a failed re-read changes nothing: the deadline we
                        // have stands. At zero it is retried; the seats are never assumed gone.
                        if (_uiState.value.payment.isUnderWay) {
                            failedPolls++
                            nextPollAt = clock.now() + recheckBackoff(failedPolls)
                        } else if (_uiState.value.phase == HoldPhase.Checking) {
                            _uiState.update { it.copy(checkError = result.error) }
                            scheduleNextCheck()
                        }
                    }
                }
            }
    }

    private suspend fun onBookingRead(booking: HeldBookingDto) {
        if (_uiState.value.phase.isEnded) return
        val receivedAt = clock.now()
        val fresh = booking.liveDeadline(receivedAt)
        val stillHeld = fresh != null
        val started = _uiState.value.payment.isUnderWay
        _uiState.update { it.copy(booking = booking, checkError = null) }
        // Only a HELD read carries the accepted methods (MV-B2): absent on PAYMENT_PENDING and later means "not sent
        // now", not "an older server", so test cash must not come and go with the polls.
        if (booking.status == HELD) _uiState.update { it.withMethods(PaymentMethod.offered(booking.paymentMethods)) }
        failedPolls = 0
        when {
            booking.status in PAID_STATUSES -> onPaid()
            booking.status == PAYMENT_PENDING -> onPaymentPending(booking, receivedAt)
            started && booking.status == EXPIRED -> {
                // The deadline passed with the payment unconfirmed. Late money still confirms it (or is refunded),
                // so the booking is read again, more slowly; nothing is assumed. The stored hold is kept while this
                // screen shows, but Home's card, date + party and a reminder tap read EXPIRED as over and forget it:
                // after leaving, the way back is My trips, where a late confirmation shows (M3-M1 review #9).
                reminders.onEnded(hold.ref)
                _uiState.update { it.copy(payment = PaymentStep.Late, phase = HoldPhase.Holding) }
                refreshClock(receivedAt)
                nextPollAt = receivedAt + LATE_POLL
            }
            !stillHeld -> {
                // Cleared before the phase change: Expired ends the screen, which clears this view
                // model and its scope, and an unfinished clear would leave the phone stored.
                activeHold.clear(hold.ref)
                reminders.onEnded(hold.ref)
                stopAll()
                _uiState.update { it.copy(phase = HoldPhase.Expired, isLeaveConfirmVisible = false) }
            }
            fresh.remaining(receivedAt) > Duration.ZERO -> {
                deadline = fresh
                // Re-scheduled from the server's fresh deadline, never the device clock alone (M3-M7).
                reminders.onHeld(hold.ref, fresh, booking.trip?.title, booking.partySize)
                emptyChecks = 0
                _uiState.update { it.copy(phase = HoldPhase.Holding).afterHeldAgain(started) }
                refreshClock(receivedAt)
            }
            else -> {
                // Held, but with nothing left: the server is about to call it EXPIRED. Ask again
                // shortly rather than in a tight loop.
                deadline = fresh
                _uiState.update { it.afterHeldAgain(started) }
                refreshClock(receivedAt)
                if (_uiState.value.phase == HoldPhase.Holding) _uiState.update { it.copy(phase = HoldPhase.Checking) }
                scheduleNextCheck()
            }
        }
    }

    /**
     * PAYMENT_PENDING: the countdown keeps the deadline the customer was given (it may be past while the server's
     * grace runs) and the booking is read again every [POLL]. Found on a read rather than started here, it is
     * [PaymentStep.Confirming]. No reminder now (M3-M1): "pay before the seats are released" no longer applies.
     */
    private fun onPaymentPending(
        booking: HeldBookingDto,
        receivedAt: Instant,
    ) {
        HoldDeadline.from(booking.holdExpiresAt, booking.serverNow, receivedAt)?.let { deadline = it }
        reminders.onEnded(hold.ref)
        _uiState.update {
            it.copy(
                phase = HoldPhase.Holding,
                payment = if (it.payment.isUnderWay && it.payment != PaymentStep.Late) it.payment else PaymentStep.Confirming,
            )
        }
        refreshClock(receivedAt)
        nextPollAt = receivedAt + POLL
    }

    /**
     * Bought: by a read that says so, or at once by test cash (MV-M4). The stored hold and its reminder are cleared
     * before the phase change, so the view model's end cannot cut them short.
     */
    private suspend fun onPaid() {
        activeHold.clear(hold.ref)
        reminders.onEnded(hold.ref)
        stopAll()
        _uiState.update { it.copy(phase = HoldPhase.Paid, isLeaveConfirmVisible = false) }
    }

    /** HELD after a payment was started here: the provider declined it, so the form is back, saying so. */
    private fun HoldUiState.afterHeldAgain(started: Boolean): HoldUiState =
        if (started) copy(payment = PaymentStep.Choosing, declined = true, checkoutUrl = null, checkoutToOpen = null) else this

    // ---- Checkout (M3-M1) ----

    fun selectMethod(method: PaymentMethod) {
        if (_uiState.value.payment != PaymentStep.Choosing || method !in _uiState.value.methods) return
        _uiState.update { it.copy(method = method, paymentError = null) }
    }

    fun onPayerNameChange(name: String) = _uiState.update { it.copy(payer = it.payer.copy(name = name)) }

    fun onPayerPhoneChange(phone: String) = _uiState.update { it.copy(payer = it.payer.copy(phone = phone)) }

    /**
     * The pay button. Fields first, then the sign-in (asked here, only when paying: the sign-in sheet, no OTP), then
     * `startPayment`. After a sign-in the person taps pay again: nothing is charged, or sent to their phone, without
     * a tap made while signed in.
     */
    fun pay() {
        val state = _uiState.value
        if (!state.canPay) return
        if (!state.payer.isValid) {
            _uiState.update { it.copy(showPayerErrors = true) }
            return
        }
        // A read sent before this payment would answer HELD after it starts and pass for a decline: it is dropped,
        // and the first poll after the start reads afresh.
        paymentGeneration++
        reread?.cancel()
        _uiState.update { it.copy(payment = PaymentStep.Starting, paymentError = null, declined = false, isLeaveConfirmVisible = false) }
        viewModelScope.launch {
            if (repository.currentSession() !is SessionState.SignedIn) {
                _uiState.update { it.copy(payment = PaymentStep.Choosing, signInRequested = true) }
                return@launch
            }
            val request =
                StartPaymentRequest(
                    method = state.method.wire,
                    payer = PayerRequest(name = state.payer.cleanName, phone = state.payer.cleanPhone),
                    returnUrl = state.checkoutReturnUrl,
                    // Always sent: the hold may not be on the account yet (made signed out, or on another install).
                    guestPhone = hold.guestPhone,
                )
            when (val result = repository.startPayment(hold.ref, request)) {
                is AppResult.Success -> onPaymentStarted(result.data)
                is AppResult.Failure -> onPaymentNotStarted(result.error)
            }
        }
    }

    private suspend fun onPaymentStarted(started: PaymentStartedDto) {
        // A read sent while it was starting (the screen came back to the front) is dropped, and frees the slot.
        paymentGeneration++
        reread?.cancel()
        // Test cash (MV-B2): already CONFIRMED when the call returns. Nothing to open, nothing to poll: straight to the
        // confirmation, which reads the booking for the ticket.
        if (started.action.type == ACTION_CONFIRMED) {
            onPaid()
            return
        }
        // Nothing to remind about while the payment completes (M3-M1 decision); a decline brings it back.
        reminders.onEnded(hold.ref)
        val url = started.action.url?.takeIf { started.action.type == ACTION_REDIRECT && it.isNotBlank() }
        _uiState.update {
            it.copy(
                payment = if (url != null) PaymentStep.AwaitingCheckout else PaymentStep.AwaitingWallet,
                checkoutUrl = url,
                checkoutToOpen = url,
                isLeaveConfirmVisible = false,
            )
        }
        failedPolls = 0
        nextPollAt = clock.now() + POLL
    }

    /** Branches on the code only; every failure leaves the form, and the booking HELD on the server's side. */
    private fun onPaymentNotStarted(error: AppError) {
        val code = (error as? AppError.Api)?.code
        when (code) {
            // The token was refused or is not a customer's: sign in (again), then pay.
            ApiErrorCodes.UNAUTHORIZED, ApiErrorCodes.IDENTITY_REQUIRED ->
                _uiState.update { it.copy(payment = PaymentStep.Choosing, signInRequested = true) }
            // Already started (another tap, another device) or paid: the booking says which.
            ApiErrorCodes.CONFLICT -> {
                _uiState.update { it.copy(payment = PaymentStep.Choosing) }
                reread()
            }
            // Too little time left, or the date was called off: said, and the booking read for what it is now. So is a
            // refused request (MV-M4): test cash switched off since the list was read drops out with the read.
            ApiErrorCodes.HOLD_EXPIRED, ApiErrorCodes.DEPARTURE_NOT_OPEN, ApiErrorCodes.VALIDATION_FAILED -> {
                _uiState.update { it.copy(payment = PaymentStep.Choosing, paymentError = error) }
                reread()
            }
            else -> _uiState.update { it.copy(payment = PaymentStep.Choosing, paymentError = error) }
        }
    }

    /**
     * The browser handed [HoldUiState.checkoutToOpen] back: closed, or come back through the return link (at once on
     * Android, whose Custom Tab reports nothing). The booking is read now rather than at the next poll.
     */
    fun onCheckoutClosed() {
        _uiState.update { it.copy(checkoutToOpen = null) }
        if (_uiState.value.payment.isUnderWay) {
            nextPollAt = clock.now()
            reread()
        }
    }

    /** "Open the payment page again": its tab was closed before paying. */
    fun reopenCheckout() {
        val state = _uiState.value
        val url = state.checkoutUrl ?: return
        if (state.payment == PaymentStep.AwaitingCheckout) _uiState.update { it.copy(checkoutToOpen = url) }
    }

    /** The screen has opened the sign-in sheet. */
    fun onSignInShown() = _uiState.update { it.copy(signInRequested = false) }

    private fun scheduleNextCheck() {
        emptyChecks++
        nextCheckAt = clock.now() + recheckBackoff(emptyChecks)
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

        /** Statuses in which the seats are still held for this booking. */
        val HELD_STATUSES = setOf("HELD", "PAYMENT_PENDING")

        /** How often a payment under way is read again while the screen shows (M3-M1). */
        val POLL = 3.seconds

        /** The slower rhythm once the deadline passed with the payment unconfirmed ([PaymentStep.Late]). */
        val LATE_POLL = 15.seconds

        private const val EXPIRED = "EXPIRED"
        private const val HELD = "HELD"
        private const val ACTION_REDIRECT = "REDIRECT"
        private const val ACTION_CONFIRMED = "CONFIRMED"
    }
}

/**
 * [offered] as the methods shown; the picked one stays if still offered, else Vodafone Cash, else the first offered.
 * Test cash comes after the real methods, so it is picked for the person only when nothing else is offered.
 */
private fun HoldUiState.withMethods(offered: List<PaymentMethod>): HoldUiState {
    val picked =
        when {
            method in offered -> method
            PaymentMethod.VodafoneCash in offered -> PaymentMethod.VodafoneCash
            else -> offered.first()
        }
    return copy(methods = offered, method = picked)
}

private fun AppError.isCode(code: String): Boolean = this is AppError.Api && this.code == code
