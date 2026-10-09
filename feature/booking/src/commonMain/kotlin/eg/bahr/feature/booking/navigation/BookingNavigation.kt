package eg.bahr.feature.booking.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.feature.booking.presentation.BookingConfirmedScreen
import eg.bahr.feature.booking.presentation.BookingScreen
import eg.bahr.feature.booking.presentation.HoldScreen

/**
 * The booking feature's public surface: destinations for the app's NavHost, and how to reach them.
 *
 * [onHeld] gets the placed hold once seats are held; the caller decides where it goes (today the
 * placeholder [holdScreen], the countdown in M2-M2).
 */
fun NavGraphBuilder.bookingScreen(
    onBack: () -> Unit,
    onHeld: (HoldRoute) -> Unit,
) {
    composable<BookingRoute> { entry ->
        val route = entry.toRoute<BookingRoute>()
        BookingScreen(slug = route.slug, departureId = route.departureId, onBack = onBack, onHeld = onHeld)
    }
}

/**
 * The held seats. A placeholder until M2-M2 builds the countdown here; it shows the reference and
 * the server's total.
 */
fun NavGraphBuilder.holdScreen(onBack: () -> Unit) {
    composable<HoldRoute> { entry ->
        HoldScreen(hold = entry.toRoute<HoldRoute>(), onBack = onBack)
    }
}

fun NavGraphBuilder.bookingConfirmedScreen(onDone: () -> Unit) {
    composable<BookingConfirmedRoute> { entry ->
        BookingConfirmedScreen(ref = entry.toRoute<BookingConfirmedRoute>().ref, onDone = onDone)
    }
}

fun NavController.navigateToBooking(
    slug: String,
    departureId: String,
) = navigate(BookingRoute(slug, departureId))

/**
 * Date + party stays under the held seats, so Back returns to date selection (HANDOFF: the back
 * button pops the stack; at 00:00 the user goes back to date selection). The screen does not
 * navigate forward again on return: it consumes the hold once.
 */
fun NavController.navigateToHold(hold: HoldRoute) = navigate(hold)

/**
 * The hold screen is finished once seats are held; going "back" into it would place a second
 * hold, so it leaves the back stack.
 */
fun NavController.navigateToBookingConfirmed(ref: String) =
    navigate(BookingConfirmedRoute(ref)) {
        popUpTo<BookingRoute> { inclusive = true }
    }
