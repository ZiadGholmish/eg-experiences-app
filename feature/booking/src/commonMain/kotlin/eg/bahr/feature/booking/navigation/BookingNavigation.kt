package eg.bahr.feature.booking.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.feature.booking.presentation.BookingConfirmedScreen
import eg.bahr.feature.booking.presentation.BookingScreen

/** The booking feature's public surface: destinations for the app's NavHost, and how to reach them. */
fun NavGraphBuilder.bookingScreen(
    onBooked: (ref: String) -> Unit,
    onHoldExpired: () -> Unit,
) {
    composable<BookingRoute> { entry ->
        BookingScreen(
            departureId = entry.toRoute<BookingRoute>().departureId,
            onBooked = onBooked,
            onHoldExpired = onHoldExpired,
        )
    }
}

fun NavGraphBuilder.bookingConfirmedScreen(onDone: () -> Unit) {
    composable<BookingConfirmedRoute> { entry ->
        BookingConfirmedScreen(ref = entry.toRoute<BookingConfirmedRoute>().ref, onDone = onDone)
    }
}

fun NavController.navigateToBooking(departureId: Long) = navigate(BookingRoute(departureId))

/**
 * The hold screen is finished once seats are held; going "back" into it would place a second
 * hold, so it leaves the back stack.
 */
fun NavController.navigateToBookingConfirmed(ref: String) =
    navigate(BookingConfirmedRoute(ref)) {
        popUpTo<BookingRoute> { inclusive = true }
    }
