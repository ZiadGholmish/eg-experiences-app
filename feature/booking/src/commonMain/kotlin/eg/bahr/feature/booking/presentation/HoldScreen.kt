package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.HoldCountdown
import eg.bahr.core.designsystem.components.StickyActionBar
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
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
            TotalPanel(state)
        }
        PayBar(state)
    }

    if (state.isLeaveConfirmVisible) {
        LeaveDialog(
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
                    SkeletonLine(widthFraction = TITLE_SKELETON)
                    SkeletonLine(widthFraction = META_SKELETON)
                } else {
                    booking.trip?.title?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
                    val party = pluralStringResource(Res.plurals.booking_party_count, booking.partySize, booking.partySize)
                    val date = booking.dayLabel ?: BahrFormat.date(booking.date)
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

@Composable
private fun SkeletonLine(widthFraction: Float) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(BahrSize.skeletonLine)
                .clip(BahrTheme.shapes.full)
                .background(BahrTheme.colors.surfaceDim),
    )
}

/** The server's total (from the hold), and the party once the booking is re-read. No client arithmetic. */
@Composable
private fun TotalPanel(state: HoldUiState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.card)
                .background(BahrTheme.colors.surfaceLow)
                .padding(BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        state.booking?.let {
            SummaryRow(
                label = stringResource(Res.string.booking_summary_party),
                value = pluralStringResource(Res.plurals.booking_party_count, it.partySize, it.partySize),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.booking_total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = BahrFormat.money(state.totalAmount, state.totalCurrency, BahrTheme.locale.isArabic),
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
private fun PayBar(state: HoldUiState) {
    StickyActionBar {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
            BahrPrimaryButton(
                text =
                    stringResource(
                        Res.string.booking_pay_cta,
                        BahrFormat.money(state.totalAmount, state.totalCurrency, BahrTheme.locale.isArabic),
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
 * "Leave and release your seats?" Neither action is coral (coral is the step forward): releasing is
 * error-coloured, keeping the seats is the primary text button. If the release fails, the dialog says
 * the seats free themselves at the deadline and offers to leave anyway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveDialog(
    state: HoldUiState,
    onConfirm: () -> Unit,
    onLeaveAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    val failed = state.releaseError != null
    // The dialog is its own window, which on Android takes its layout direction from the platform
    // locale rather than the app's chosen language, so the app's direction is carried in by hand.
    val direction = LocalLayoutDirection.current
    BasicAlertDialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = BahrSpacing.gutter)
                        .clip(BahrTheme.shapes.card)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(BahrSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
            ) {
                Text(stringResource(Res.string.booking_leave_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(Res.string.booking_leave_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // One message for every failure (no connection, server error, payment already started):
                // whatever the cause, the way out is the same.
                if (failed) {
                    Text(
                        stringResource(Res.string.booking_leave_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm, Alignment.End)) {
                    TextButton(onClick = onDismiss, enabled = !state.isReleasing) {
                        Text(stringResource(Res.string.booking_leave_stay))
                    }
                    TextButton(onClick = if (failed) onLeaveAnyway else onConfirm, enabled = !state.isReleasing) {
                        Text(
                            text = stringResource(if (failed) Res.string.booking_leave_anyway else Res.string.booking_leave_confirm),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** The scrolling content, for tests. */
internal const val HOLD_CONTENT_TAG = "hold_content"

/** The "you already have seats on hold" notice, for tests. */
internal const val ALREADY_HELD_TAG = "hold_already_held"

private const val TITLE_SKELETON = .8f
private const val META_SKELETON = .5f
