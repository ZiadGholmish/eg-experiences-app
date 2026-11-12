package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eg.bahr.core.designsystem.components.BahrBottomSheet
import eg.bahr.core.designsystem.components.BahrBottomSheetContent
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.components.HoldCountdown
import eg.bahr.core.designsystem.components.StickyActionBar
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.booking_dates_overnight
import eg.bahr.core.localization.generated.resources.booking_departs_at
import eg.bahr.core.localization.generated.resources.booking_departs_from
import eg.bahr.core.localization.generated.resources.booking_hold_already_held
import eg.bahr.core.localization.generated.resources.booking_hold_check_failed
import eg.bahr.core.localization.generated.resources.booking_hold_checking
import eg.bahr.core.localization.generated.resources.booking_hold_label
import eg.bahr.core.localization.generated.resources.booking_hold_minutes_left
import eg.bahr.core.localization.generated.resources.booking_hold_time_up
import eg.bahr.core.localization.generated.resources.booking_leave_anyway
import eg.bahr.core.localization.generated.resources.booking_leave_body
import eg.bahr.core.localization.generated.resources.booking_leave_confirm
import eg.bahr.core.localization.generated.resources.booking_leave_failed
import eg.bahr.core.localization.generated.resources.booking_leave_paying_body
import eg.bahr.core.localization.generated.resources.booking_leave_paying_confirm
import eg.bahr.core.localization.generated.resources.booking_leave_paying_stay
import eg.bahr.core.localization.generated.resources.booking_leave_paying_title
import eg.bahr.core.localization.generated.resources.booking_leave_stay
import eg.bahr.core.localization.generated.resources.booking_leave_title
import eg.bahr.core.localization.generated.resources.booking_party_count
import eg.bahr.core.localization.generated.resources.booking_pay_awaiting_checkout
import eg.bahr.core.localization.generated.resources.booking_pay_awaiting_wallet
import eg.bahr.core.localization.generated.resources.booking_pay_confirming
import eg.bahr.core.localization.generated.resources.booking_pay_cta
import eg.bahr.core.localization.generated.resources.booking_pay_foot
import eg.bahr.core.localization.generated.resources.booking_pay_late
import eg.bahr.core.localization.generated.resources.booking_pay_reopen
import eg.bahr.core.localization.generated.resources.booking_pay_step_title
import eg.bahr.core.localization.generated.resources.booking_pay_test_cta
import eg.bahr.core.localization.generated.resources.booking_pay_vf_cta
import eg.bahr.core.localization.generated.resources.booking_pay_waiting_cta
import eg.bahr.core.localization.generated.resources.booking_summary_party
import eg.bahr.core.localization.generated.resources.booking_total
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.model.laterReturnDate
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.components.BookingThumbnail
import eg.bahr.feature.booking.presentation.components.BookingTopBar
import eg.bahr.feature.booking.presentation.components.PayerFields
import eg.bahr.feature.booking.presentation.components.PaymentMethods
import eg.bahr.feature.booking.presentation.components.PaymentProblem
import eg.bahr.feature.booking.presentation.components.SummaryRow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The held seats (HANDOFF screen 5, "Payment", step 2 of 3): the countdown panel, the trip summary
 * and the total, then the checkout (M3-M1): "Pay with" (Vodafone Cash, the default, or card), the
 * picked method's fields and the pay button; since MV-M4 also test cash, last, where the server lists it, which
 * confirms at once and hands over with [onPaid] without any checkout. Paying opens the provider's hosted checkout ([browser]);
 * the screen then waits for the booking to read CONFIRMED and hands over with [onPaid]. Paying needs
 * a sign-in: [onSignIn] opens the sign-in sheet, and the screen is back under it afterwards.
 *
 * Text first: the countdown and the total come with the route and draw at once; the summary (title,
 * date, party, where the bus leaves) arrives with `GET /bookings/{ref}`, its thumbnail size reserved.
 *
 * Back (top bar or system) asks before giving the seats up; [onEnded] is called once the hold is
 * over, `true` when it ran out, `false` when the user left.
 *
 * [reminderPrompt] is placed for a hold just placed (M3-M7): the push pre-prompt, asking for the notification
 * permission the hold reminder needs; [onReminderPromptDone] once it is answered or not needed.
 */
@Composable
internal fun HoldScreen(
    hold: HoldRoute,
    onEnded: (holdExpired: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onPaid: (ref: String) -> Unit = {},
    onSignIn: () -> Unit = {},
    alreadyHeldNotice: Boolean = false,
    onAlreadyHeldNoticeSeen: () -> Unit = {},
    reminderPrompt: (@Composable (onDone: () -> Unit) -> Unit)? = null,
    onReminderPromptDone: () -> Unit = {},
    viewModel: HoldViewModel = koinViewModel { parametersOf(hold) },
    browser: CheckoutBrowser = rememberCheckoutBrowser(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // STARTED, not composition: also covers coming back from the background, and back from a trip a
    // link opened over this screen. Each start re-reads the deadline from the server.
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose {
            viewModel.onStop()
            // The "already held" notice is for this return only.
            onAlreadyHeldNoticeSeen()
        }
    }

    // One-shot: the VM marks the end as reported, so a re-run of this effect (a configuration change
    // before the pop lands) cannot pop a second time.
    LaunchedEffect(state.phase, state.isEndReported) {
        if (!state.phase.isEnded || state.isEndReported) return@LaunchedEffect
        viewModel.onEndReported()
        if (state.phase == HoldPhase.Paid) onPaid(state.ref) else onEnded(state.phase == HoldPhase.Expired)
    }

    // The hosted checkout (M3-M1). Cleared only once the browser hands back (onCheckoutClosed), so the key does not
    // change, and cancel the iOS session, while it is open.
    LaunchedEffect(state.checkoutToOpen) {
        val url = state.checkoutToOpen ?: return@LaunchedEffect
        browser.open(url, state.checkoutReturnUrl)
        viewModel.onCheckoutClosed()
    }

    // Paying needs a sign-in: the sheet opens over this screen, which reads the session again under it.
    LaunchedEffect(state.signInRequested) {
        if (!state.signInRequested) return@LaunchedEffect
        viewModel.onSignInShown()
        onSignIn()
    }

    // System back and the iOS back gesture ask first too, like the top bar's arrow.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !state.phase.isEnded,
        onBackCompleted = viewModel::requestLeave,
    )

    // The payer's fields (M3-M1) sit above the sticky pay bar: the keyboard must push both up (edge-to-edge, as
    // BookingScreen does), or it covers the field being typed into.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        BookingTopBar(title = stringResource(Res.string.booking_pay_step_title), step = 2, onBack = viewModel::requestLeave)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(HOLD_CONTENT_TAG)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
        ) {
            Countdown(state)
            PaymentProgress(payment = state.payment)
            if (alreadyHeldNotice) {
                Text(
                    text = stringResource(Res.string.booking_hold_already_held),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ALREADY_HELD_TAG),
                )
            }
            TripSummary(state.booking)
            // The fields each reads, not the whole state: those skip the 500 ms ticks (Compose audit L15).
            TotalPanel(partySize = state.booking?.partySize, totalAmount = state.totalAmount, totalCurrency = state.totalCurrency)
            // The form until a payment is under way; then the progress line above says what happens.
            if (!state.payment.isUnderWay) {
                val editable = state.payment == PaymentStep.Choosing && !state.phase.isEnded
                PaymentProblem(error = state.paymentError, declined = state.declined)
                PaymentMethods(methods = state.methods, selected = state.method, enabled = editable, onSelect = viewModel::selectMethod)
                PayerFields(
                    method = state.method,
                    payer = state.payer,
                    showErrors = state.showPayerErrors,
                    enabled = editable,
                    onNameChange = viewModel::onPayerNameChange,
                    onPhoneChange = viewModel::onPayerPhoneChange,
                    onDone = viewModel::pay,
                )
            }
        }
        PayBar(
            look = payBarLook(state),
            totalAmount = state.totalAmount,
            totalCurrency = state.totalCurrency,
            phone = state.payer.takeIf { state.method == PaymentMethod.VodafoneCash && it.phoneValid }?.cleanPhone,
            testCash = state.method == PaymentMethod.TestCash,
            onPay = viewModel::pay,
            onReopen = viewModel::reopenCheckout,
        )
    }

    // A hold just placed (M3-M7): feature:notifications' pre-prompt, if notifications are off, so the "about to
    // expire" reminder can show. It decides itself whether to ask; once it is done it is not placed again.
    if (reminderPrompt != null && !state.phase.isEnded) {
        reminderPrompt {
            onReminderPromptDone()
            viewModel.onReminderPromptDone()
        }
    }

    if (state.isLeaveConfirmVisible) {
        LeaveSheet(
            state = state,
            onConfirm = viewModel::confirmLeave,
            onLeaveAnyway = viewModel::leaveWithoutRelease,
            onDismiss = viewModel::dismissLeave,
        )
    }
}

@Composable
private fun Countdown(state: HoldUiState) {
    // Past the visible deadline with a payment under way (the server's grace, or Late): "Seats held for 00:00" would
    // contradict the progress line under it, which then says what is happening instead (M3-M1 review #5).
    if (state.payment.isUnderWay && state.secondsLeft == 0) return
    val announcement =
        if (state.payment.isUnderWay && state.minutesLeft == 0) {
            // Under a minute left while paying the seats are still kept (the server's grace): not "time is up".
            stringResource(Res.string.booking_pay_confirming)
        } else if (state.minutesLeft > 0) {
            pluralStringResource(Res.plurals.booking_hold_minutes_left, state.minutesLeft, state.minutesLeft)
        } else {
            stringResource(Res.string.booking_hold_time_up)
        }
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        HoldCountdown(
            secondsLeft = state.secondsLeft,
            label = stringResource(Res.string.booking_hold_label),
            progress = state.progress,
            icon = BahrIcons.Timer.filled(),
            announcement = announcement,
        )
        if (state.phase == HoldPhase.Checking && !state.payment.isUnderWay) {
            val failed = state.checkError != null
            Text(
                text = stringResource(if (failed) Res.string.booking_hold_check_failed else Res.string.booking_hold_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Thumbnail, title, "Sat 7 Nov · 2 people" and where the bus leaves; lines wait for the re-read. */
@Composable
private fun TripSummary(booking: HeldBookingDto?) {
    BahrCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(BahrSpacing.md), horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            BookingThumbnail(booking?.trip?.cardImage)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                if (booking == null) {
                    // Pulsing like every placeholder now (M3-M14); before, these two lines were still.
                    BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = TITLE_SKELETON))
                    BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = META_SKELETON))
                } else {
                    booking.trip?.title?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
                    val party = pluralStringResource(Res.plurals.booking_party_count, booking.partySize, booking.partySize)
                    val leaves = booking.dayLabel ?: BahrFormat.date(booking.date)
                    // A multi-day booking (M4-B0b) says the day it is back too.
                    val date =
                        booking.laterReturnDate?.let {
                            stringResource(Res.string.booking_dates_overnight, leaves, BahrFormat.date(it))
                        } ?: leaves
                    SecondaryLine(stringResource(Res.string.format_pair, date, party))
                    departsLine(booking)?.let { SecondaryLine(it) }
                }
            }
        }
    }
}

@Composable
private fun departsLine(booking: HeldBookingDto): String? {
    val place = booking.departure?.placeName ?: return null
    val time = booking.departure.timeLocal?.let(BahrFormat::ltr) ?: return null
    val city = booking.departure.city
    return if (city != null) {
        stringResource(Res.string.booking_departs_from, city, place, time)
    } else {
        stringResource(Res.string.booking_departs_at, place, time)
    }
}

@Composable
private fun SecondaryLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The server's total (from the hold), and the party once the booking is re-read. No client arithmetic. */
@Composable
private fun TotalPanel(
    partySize: Int?,
    totalAmount: Long,
    totalCurrency: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.card)
                // White on the off-white page (M4-M12): `surfaceLow` would vanish into it.
                .background(MaterialTheme.colorScheme.surface)
                .padding(BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        partySize?.let {
            SummaryRow(
                label = stringResource(Res.string.booking_summary_party),
                value = pluralStringResource(Res.plurals.booking_party_count, it, it),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.booking_total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = BahrFormat.money(totalAmount, totalCurrency, BahrTheme.locale.isArabic),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** What the pay bar offers, from the payment's step. */
internal enum class PayBarLook {
    /** "Pay 900 EGP" / "Send request to 010…", live while the seats are held with time left. */
    Pay,

    /** The same, spinning: the payment is being started. */
    Starting,

    /** The same, off: the countdown is at zero, or the screen is leaving. */
    Off,

    /** A payment page was opened and may be opened again. */
    Reopen,

    /** Nothing to do but wait for the provider's answer. */
    Waiting,
}

internal fun payBarLook(state: HoldUiState): PayBarLook =
    when (state.payment) {
        PaymentStep.Choosing -> if (state.canPay) PayBarLook.Pay else PayBarLook.Off
        PaymentStep.Starting -> PayBarLook.Starting
        PaymentStep.AwaitingCheckout -> if (state.checkoutUrl != null) PayBarLook.Reopen else PayBarLook.Waiting
        PaymentStep.AwaitingWallet, PaymentStep.Confirming, PaymentStep.Late -> PayBarLook.Waiting
    }

/**
 * The sticky pay button with `lock` (HANDOFF screen 5): "Pay 900 EGP" for card, "Send request to 010…" for Vodafone
 * Cash once its number is valid ([phone], left to right inside Arabic), then the footnote. While a payment is under
 * way it reopens the payment page, or waits.
 */
@Composable
private fun PayBar(
    look: PayBarLook,
    totalAmount: Long,
    totalCurrency: String,
    phone: String?,
    onPay: () -> Unit,
    onReopen: () -> Unit,
    testCash: Boolean = false,
) {
    val total = BahrFormat.money(totalAmount, totalCurrency, BahrTheme.locale.isArabic)
    val text =
        when (look) {
            PayBarLook.Reopen -> stringResource(Res.string.booking_pay_reopen)
            PayBarLook.Waiting -> stringResource(Res.string.booking_pay_waiting_cta)
            else ->
                when {
                    // Test cash takes no money (MV-M4 review #4): "Pay 900 EGP" would say otherwise.
                    testCash -> stringResource(Res.string.booking_pay_test_cta)
                    phone != null -> stringResource(Res.string.booking_pay_vf_cta, BahrFormat.ltr(phone))
                    else -> stringResource(Res.string.booking_pay_cta, total)
                }
        }
    StickyActionBar {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            BahrPrimaryButton(
                text = text,
                onClick = if (look == PayBarLook.Reopen) onReopen else onPay,
                enabled = look == PayBarLook.Pay || look == PayBarLook.Reopen,
                loading = look == PayBarLook.Starting || look == PayBarLook.Waiting,
                leadingIcon = BahrIcons.Lock.outlined(),
                modifier = Modifier.fillMaxWidth().testTag(PAY_BUTTON_TAG),
            )
            Text(
                text = stringResource(Res.string.booking_pay_foot),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What happens while a payment is under way, under the countdown: finish on the payment page, approve on the phone,
 * we're confirming, or the time ran out before it was confirmed. Announced as it changes.
 */
@Composable
private fun PaymentProgress(payment: PaymentStep) {
    val text =
        when (payment) {
            PaymentStep.AwaitingCheckout -> Res.string.booking_pay_awaiting_checkout
            PaymentStep.AwaitingWallet -> Res.string.booking_pay_awaiting_wallet
            PaymentStep.Confirming -> Res.string.booking_pay_confirming
            PaymentStep.Late -> Res.string.booking_pay_late
            PaymentStep.Choosing, PaymentStep.Starting -> return
        }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.card)
                .background(MaterialTheme.colorScheme.surface)
                .padding(BahrSpacing.lg)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(PAYMENT_PROGRESS_TAG),
    )
}

/**
 * "Leave and release your seats?", a bottom sheet since M3-M0b (user decision 2026-10-14: sheets instead
 * of dialogs). Neither action is coral (coral is the step forward): releasing is the destructive fill,
 * keeping the seats is the way out, last, with the default focus. If the release fails, the sheet says the
 * seats free themselves at the deadline and offers to leave anyway.
 */
@Composable
private fun LeaveSheet(
    state: HoldUiState,
    onConfirm: () -> Unit,
    onLeaveAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    BahrBottomSheet(props = leaveProps(state, onConfirm, onLeaveAnyway, onDismiss)) { LeaveBody(state) }
}

/** The leave sheet's inside, for the screenshot tests (a sheet is a second window they cannot draw). */
@Composable
internal fun LeaveSheetContent(state: HoldUiState) {
    BahrBottomSheetContent(leaveProps(state, {}, {}, {})) { LeaveBody(state) }
}

@Composable
private fun leaveProps(
    state: HoldUiState,
    onConfirm: () -> Unit,
    onLeaveAnyway: () -> Unit,
    onDismiss: () -> Unit,
): BahrBottomSheet.Props {
    if (state.payment.isUnderWay) return leavePayingProps(onConfirm, onDismiss)
    val failed = state.releaseError != null
    return BahrBottomSheet.Props(
        title = stringResource(Res.string.booking_leave_title),
        onDismiss = onDismiss,
        // Stays up while releasing, so a failed release can say "leave anyway" in it (review R4-2).
        dismissible = !state.isReleasing,
        actions =
            listOf(
                BahrBottomSheet.Action(
                    text = stringResource(if (failed) Res.string.booking_leave_anyway else Res.string.booking_leave_confirm),
                    style = BahrBottomSheet.Action.Style.Destructive,
                    onClick = if (failed) onLeaveAnyway else onConfirm,
                    loading = state.isReleasing,
                    testTag = LEAVE_CONFIRM_TAG,
                ),
            ),
        cancel =
            BahrBottomSheet.Action(
                text = stringResource(Res.string.booking_leave_stay),
                onClick = onDismiss,
                enabled = !state.isReleasing,
                testTag = LEAVE_STAY_TAG,
            ),
    )
}

/**
 * While a payment is under way (M3-M1) the seats cannot be released, so leaving gives nothing up: a plain action, no
 * "release" and no "leave anyway".
 */
@Composable
private fun leavePayingProps(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
): BahrBottomSheet.Props =
    BahrBottomSheet.Props(
        title = stringResource(Res.string.booking_leave_paying_title),
        onDismiss = onDismiss,
        actions =
            listOf(
                BahrBottomSheet.Action(
                    text = stringResource(Res.string.booking_leave_paying_confirm),
                    style = BahrBottomSheet.Action.Style.Primary,
                    onClick = onConfirm,
                    testTag = LEAVE_CONFIRM_TAG,
                ),
            ),
        cancel =
            BahrBottomSheet.Action(
                text = stringResource(Res.string.booking_leave_paying_stay),
                onClick = onDismiss,
                testTag = LEAVE_STAY_TAG,
            ),
    )

@Composable
private fun LeaveBody(state: HoldUiState) {
    Text(
        stringResource(if (state.payment.isUnderWay) Res.string.booking_leave_paying_body else Res.string.booking_leave_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // One message for every failure (no connection, server error, payment already started): whatever
    // the cause, the way out is the same.
    if (state.releaseError != null) {
        Text(
            stringResource(Res.string.booking_leave_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** The leave sheet's buttons, for tests. */
internal const val LEAVE_CONFIRM_TAG = "hold_leave_confirm"
internal const val LEAVE_STAY_TAG = "hold_leave_stay"

/** The pay button and the payment's progress line, for tests. */
internal const val PAY_BUTTON_TAG = "hold_pay"
internal const val PAYMENT_PROGRESS_TAG = "hold_payment_progress"

/** The scrolling content, for tests. */
internal const val HOLD_CONTENT_TAG = "hold_content"

/** The "you already have seats on hold" notice, for tests. */
internal const val ALREADY_HELD_TAG = "hold_already_held"

private const val TITLE_SKELETON = .8f
private const val META_SKELETON = .5f
