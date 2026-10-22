package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import eg.bahr.core.localization.generated.resources.booking_leave_stay
import eg.bahr.core.localization.generated.resources.booking_leave_title
import eg.bahr.core.localization.generated.resources.booking_party_count
import eg.bahr.core.localization.generated.resources.booking_pay_coming
import eg.bahr.core.localization.generated.resources.booking_pay_cta
import eg.bahr.core.localization.generated.resources.booking_pay_step_title
import eg.bahr.core.localization.generated.resources.booking_summary_party
import eg.bahr.core.localization.generated.resources.booking_total
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.model.laterReturnDate
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.components.BookingThumbnail
import eg.bahr.feature.booking.presentation.components.BookingTopBar
import eg.bahr.feature.booking.presentation.components.SummaryRow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The held seats (HANDOFF screen 5, "Payment", step 2 of 3): the countdown panel, the trip summary
 * and the total, then the pay button.
 *
 * Payment is M3, so the pay button is drawn disabled with a note saying paying in the app is coming;
 * the payment-method rows and payer fields are left out until they do something.
 *
 * Text first: the countdown and the total come with the route and draw at once; the summary (title,
 * date, party, where the bus leaves) arrives with `GET /bookings/{ref}`, its thumbnail size reserved.
 *
 * Back (top bar or system) asks before giving the seats up; [onEnded] is called once the hold is
 * over, `true` when it ran out, `false` when the user left.
 */
@Composable
internal fun HoldScreen(
    hold: HoldRoute,
    onEnded: (holdExpired: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    alreadyHeldNotice: Boolean = false,
    onAlreadyHeldNoticeSeen: () -> Unit = {},
    viewModel: HoldViewModel = koinViewModel { parametersOf(hold) },
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
        onEnded(state.phase == HoldPhase.Expired)
    }

    // System back and the iOS back gesture ask first too, like the top bar's arrow.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !state.phase.isEnded,
        onBackCompleted = viewModel::requestLeave,
    )

    Column(modifier = modifier.fillMaxSize()) {
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
        }
        PayBar(totalAmount = state.totalAmount, totalCurrency = state.totalCurrency)
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
    val announcement =
        if (state.minutesLeft > 0) {
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
        if (state.phase == HoldPhase.Checking) {
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

/**
 * The pay button, disabled until M3 adds payment, and the note saying so. Disabled rather than
 * hidden so the step reads as the handoff's "Payment" screen and the total sits where it will.
 */
@Composable
private fun PayBar(
    totalAmount: Long,
    totalCurrency: String,
) {
    StickyActionBar {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            BahrPrimaryButton(
                text =
                    stringResource(
                        Res.string.booking_pay_cta,
                        BahrFormat.money(totalAmount, totalCurrency, BahrTheme.locale.isArabic),
                    ),
                onClick = {},
                enabled = false,
                leadingIcon = BahrIcons.Lock.outlined(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.booking_pay_coming),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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

@Composable
private fun LeaveBody(state: HoldUiState) {
    Text(
        stringResource(Res.string.booking_leave_body),
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

/** The scrolling content, for tests. */
internal const val HOLD_CONTENT_TAG = "hold_content"

/** The "you already have seats on hold" notice, for tests. */
internal const val ALREADY_HELD_TAG = "hold_already_held"

private const val TITLE_SKELETON = .8f
private const val META_SKELETON = .5f
