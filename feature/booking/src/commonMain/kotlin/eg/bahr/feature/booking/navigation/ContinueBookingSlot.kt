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
 *
 * The card comes in and goes out with an animation (M4-M4: fade + grow/shrink, so what is below
 * moves smoothly) when a hold appears or ends. [modifier] is put on the card *inside* that
 * animation: give the space above the card here (padding), and it grows and shrinks with the card.
 */
@Composable
fun ContinueBookingSlot(
    onOpen: (HoldRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContinueBookingCard(onOpen = onOpen, modifier = modifier)
}
