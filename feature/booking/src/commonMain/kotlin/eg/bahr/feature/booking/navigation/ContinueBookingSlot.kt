package eg.bahr.feature.booking.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eg.bahr.feature.booking.presentation.ContinueBookingCard

/**
 * "Continue your booking": the device's live seat hold as a card, for another feature's screen to
 * host (Home, the trip list, via its header slot). Draws nothing while there is no live hold.
 *
 * The booking feature owns the card because it owns the booking (`GET /bookings/{ref}`) and the
 * countdown; the host screen only gives it a place, and `:composeApp` joins the two, so neither
 * feature depends on the other. [onOpen] gets the hold to open; wire it to [navigateToHold].
 */
@Composable
fun ContinueBookingSlot(
    onOpen: (HoldRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContinueBookingCard(onOpen = onOpen, modifier = modifier)
}
