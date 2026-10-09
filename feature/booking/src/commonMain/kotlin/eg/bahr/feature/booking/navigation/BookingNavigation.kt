package eg.bahr.feature.booking.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.feature.booking.presentation.BookingConfirmedScreen
import eg.bahr.feature.booking.presentation.BookingScreen
import eg.bahr.feature.booking.presentation.HoldScreen

/**
 * The booking feature's public surface: destinations for the app's NavHost, and how to reach them.
 *
 * [onHeld] gets the placed hold once seats are held; the caller decides where it goes (the held
 * seats, [holdScreen]).
 */
fun NavGraphBuilder.bookingScreen(
    onBack: () -> Unit,
    onHeld: (HoldRoute) -> Unit,
) {
    composable<BookingRoute> { entry ->
        val route = entry.toRoute<BookingRoute>()
        // Set by [returnToDateSelection] when the held seats above this entry end.
        val holdEnded by entry.savedStateHandle.getStateFlow<String?>(HOLD_ENDED_KEY, null).collectAsStateWithLifecycle()
        BookingScreen(
            slug = route.slug,
            departureId = route.departureId,
            onBack = onBack,
            onHeld = onHeld,
            holdEnded = holdEnded?.let { it == HOLD_ENDED_EXPIRED },
            onHoldEndedHandled = { entry.savedStateHandle[HOLD_ENDED_KEY] = null },
        )
    }
}

/**
 * The held seats and their countdown. [onEnded] is called once the hold is over, [holdExpired] true
 * when it ran out and false when the user released it; wire it to [returnToDateSelection].
 */
fun NavGraphBuilder.holdScreen(onEnded: (holdExpired: Boolean) -> Unit) {
    composable<HoldRoute> { entry ->
        // Set by [navigateToBooking] when Continue elsewhere was turned back to this hold.
        val alreadyHeld by entry.savedStateHandle.getStateFlow(ALREADY_HELD_KEY, false).collectAsStateWithLifecycle()
        HoldScreen(
            hold = entry.toRoute<HoldRoute>(),
            onEnded = onEnded,
            alreadyHeldNotice = alreadyHeld,
            onAlreadyHeldNoticeSeen = { entry.savedStateHandle[ALREADY_HELD_KEY] = false },
        )
    }
}

fun NavGraphBuilder.bookingConfirmedScreen(onDone: () -> Unit) {
    composable<BookingConfirmedRoute> { entry ->
        BookingConfirmedScreen(ref = entry.toRoute<BookingConfirmedRoute>().ref, onDone = onDone)
    }
}

/**
 * Date + party for a trip's date, unless seats are already held: then back to that live countdown
 * instead, with a notice. A trip opened by a link sits on top of a live hold (Back returns to the
 * countdown), and Continue from it must not start a second hold while the first keeps its seats off
 * sale (M2-M2 review #1).
 */
fun NavController.navigateToBooking(
    slug: String,
    departureId: String,
) {
    if (hasEntry<HoldRoute>()) {
        getBackStackEntry<HoldRoute>().savedStateHandle[ALREADY_HELD_KEY] = true
        popBackStack<HoldRoute>(inclusive = false)
    } else {
        navigate(BookingRoute(slug, departureId))
    }
}

/**
 * Date + party stays under the held seats, so Back returns to date selection (HANDOFF: the back
 * button pops the stack; at 00:00 the user goes back to date selection). The screen does not
 * navigate forward again on return: it consumes the hold once.
 */
fun NavController.navigateToHold(hold: HoldRoute) = navigate(hold)

/**
 * Back from the held seats to date + party under them (HANDOFF: at 00:00 the user goes back to date
 * selection), telling it whether the hold ran out so it can say so; it re-reads its dates either way.
 *
 * Pops up to the topmost held-seats entry rather than "one back": if a trip opened by a link sits
 * above the hold, the hold is not what plain `popBackStack()` would remove. Only the held-seats screen
 * calls this, and only while it is showing, so the entry under it is its date + party.
 */
fun NavController.returnToDateSelection(holdExpired: Boolean) {
    if (currentBackStackEntry?.destination?.hasRoute<HoldRoute>() == true) {
        previousBackStackEntry?.savedStateHandle?.set(HOLD_ENDED_KEY, if (holdExpired) HOLD_ENDED_EXPIRED else HOLD_ENDED_RELEASED)
    }
    popBackStack<HoldRoute>(inclusive = true)
}

/**
 * True while a booking is under way on the back stack: date + party, or held seats. A link opened
 * then goes on top of it instead of replacing it (decided 2026-10-09: Back returns to the
 * countdown and the hold stays alive).
 */
fun NavController.hasBookingInProgress(): Boolean = hasEntry<BookingRoute>() || hasEntry<HoldRoute>()

private inline fun <reified T : Any> NavController.hasEntry(): Boolean =
    try {
        getBackStackEntry<T>()
        true
    } catch (absent: IllegalArgumentException) {
        // getBackStackEntry answers "not on the stack" only by throwing.
        false
    }

internal const val ALREADY_HELD_KEY = "already_held"
internal const val HOLD_ENDED_KEY = "hold_ended"
internal const val HOLD_ENDED_EXPIRED = "expired"
internal const val HOLD_ENDED_RELEASED = "released"

/**
 * The hold screen is finished once seats are held; going "back" into it would place a second
 * hold, so it leaves the back stack.
 */
fun NavController.navigateToBookingConfirmed(ref: String) =
    navigate(BookingConfirmedRoute(ref)) {
        popUpTo<BookingRoute> { inclusive = true }
    }
