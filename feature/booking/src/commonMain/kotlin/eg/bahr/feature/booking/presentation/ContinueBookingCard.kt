package eg.bahr.feature.booking.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.HoldCountdown
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.booking_continue_title
import eg.bahr.core.localization.generated.resources.booking_dates_overnight
import eg.bahr.core.localization.generated.resources.booking_hold_label
import eg.bahr.core.localization.generated.resources.booking_party_count
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.feature.booking.model.laterReturnDate
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.components.BookingThumbnail
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home's "Continue your booking" card (PLAN M2-M4): the live hold's countdown, trip, date and party;
 * tapping it opens the held seats ([onOpen]).
 *
 * Draws nothing while there is no live hold (an empty, zero-size box): it sits in the trip list's
 * header, which gives it its gap above as [modifier] so no gap shows without it.
 *
 * The view model lives as long as the screen that hosts the card (Home's back-stack entry), and each
 * time that screen starts the hold is read again from the server; scrolling the card out of view and
 * back does not.
 */
@Composable
internal fun ContinueBookingCard(
    onOpen: (HoldRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContinueBookingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The host screen's lifecycle, not this composable's: scrolling the card out of the list and
    // back must not read the booking again (see ContinueBookingViewModel.attach).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) { viewModel.attach(lifecycle) }

    // The route is built at the tap, with the server's clock as of the tap.
    AnimatedContinueBookingCard(state = state, onOpen = { viewModel.routeForTap()?.let(onOpen) }, modifier = modifier)
}

/**
 * The card coming and going (M4-M4): when a hold appears it fades in while its space opens, and when
 * it ends (ran out, released, paid, or found gone when Home starts) it fades out while its space
 * closes, so what is below moves up smoothly rather than jumping. While it shows, a new tick only
 * redraws it ([contentKey] is "shown or not"), and the leaving card is drawn from the last state that
 * showed it: the view model has already cleared it by then.
 *
 * [modifier] goes on the card itself, inside the animation, so space the host puts above the card
 * opens and closes with it. Instant under reduce motion ([bahrTween]).
 */
@Composable
private fun AnimatedContinueBookingCard(
    state: ContinueBookingUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fade = bahrTween<Float>()
    val size = bahrTween<IntSize>()
    AnimatedContent(
        targetState = state.takeIf { it.isVisible },
        contentKey = { it != null },
        transitionSpec = {
            // Clipped, so the leaving card is cut at the closing edge rather than drawn over what is
            // below it.
            fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = true) { _, _ -> size }
        },
        contentAlignment = Alignment.TopStart,
    ) { shown ->
        if (shown != null) ContinueBookingCard(state = shown, onOpen = onOpen, modifier = modifier)
    }
}

/** Stateless, for screenshots. */
@Composable
internal fun ContinueBookingCard(
    state: ContinueBookingUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val booking = state.booking ?: return
    BahrCard(
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button }.testTag(CONTINUE_BOOKING_TAG),
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.padding(BahrSpacing.md), verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            Text(text = stringResource(Res.string.booking_continue_title), style = MaterialTheme.typography.titleMedium)
            // No spoken minute announcements here (unlike the hold screen): a polite live region
            // talking every minute while the user browses trips would be noise.
            HoldCountdown(
                secondsLeft = state.secondsLeft,
                label = stringResource(Res.string.booking_hold_label),
                progress = state.progress,
                icon = BahrIcons.Timer.filled(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                BookingThumbnail(booking.trip?.cardImage)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
                    booking.trip?.title?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
                    val party = pluralStringResource(Res.plurals.booking_party_count, booking.partySize, booking.partySize)
                    val leaves = booking.dayLabel ?: BahrFormat.date(booking.date)
                    // A multi-day hold (M4-B0b) names the day it is back, worded as on the hold screen.
                    val date =
                        booking.laterReturnDate?.let {
                            stringResource(Res.string.booking_dates_overnight, leaves, BahrFormat.date(it))
                        } ?: leaves
                    Text(
                        text = stringResource(Res.string.format_pair, date, party),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Forward, so it mirrors in RTL on its own.
                Icon(
                    imageVector = BahrIcons.ArrowForward.outlined(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The card, for tests. */
internal const val CONTINUE_BOOKING_TAG = "continue_booking"
