package eg.bahr.feature.booking.presentation

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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.components.HoldCountdown
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.booking_continue_title
import eg.bahr.core.localization.generated.resources.booking_hold_label
import eg.bahr.core.localization.generated.resources.booking_party_count
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.components.BookingThumbnail
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home's "Continue your booking" card (PLAN M2-M4): the live hold's countdown, trip, date and party;
 * tapping it opens the held seats ([onOpen]).
 *
 * Draws nothing at all, not even an empty box, while there is no live hold: it sits in the trip
 * list's header, whose spacing would otherwise show a gap.
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
    ContinueBookingCard(state = state, onOpen = { viewModel.routeForTap()?.let(onOpen) }, modifier = modifier)
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
                    val date = booking.dayLabel ?: BahrFormat.date(booking.date)
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
