package eg.bahr.feature.booking.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.booking.presentation.BookingConfirmedScreen
import eg.bahr.feature.booking.presentation.BookingScreen
import eg.bahr.feature.booking.presentation.HoldScreen
import eg.bahr.feature.booking.presentation.OpenHoldScreen

/**
 * The booking feature's public surface: destinations for the app's NavHost, and how to reach them.
 *
 * [onHeld] gets the placed hold once seats are held; the caller decides where it goes (the held
 * seats, [holdScreen], via [navigateToHold]). `alreadyHeld` is true when, instead of placing a second
 * hold, the device's stored live hold is handed back (M2-M4).
 */
fun NavGraphBuilder.bookingScreen(
    onBack: () -> Unit,
    onHeld: (hold: HoldRoute, alreadyHeld: Boolean) -> Unit,
) {
    composable<BookingRoute> { entry ->
        val route = entry.toRoute<BookingRoute>()
        // Set by [returnToDateSelection] when the held seats above this entry end.
        val holdEnded by entry.savedStateHandle.getStateFlow<String?>(HOLD_ENDED_KEY, null).collectAsStateWithLifecycle()
        BahrDestination(this) {
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
}

/**
 * The held seats and their countdown. [onEnded] is called once the hold is over, [holdExpired] true
 * when it ran out and false when the user released it; wire it to [returnToDateSelection].
 */
fun NavGraphBuilder.holdScreen(
    onEnded: (holdExpired: Boolean) -> Unit,
    reminderPrompt: (@Composable (onDone: () -> Unit) -> Unit)? = null,
) {
    composable<HoldRoute> { entry ->
        // Set by [navigateToBooking] when Continue elsewhere was turned back to this hold.
        val alreadyHeld by entry.savedStateHandle.getStateFlow(ALREADY_HELD_KEY, false).collectAsStateWithLifecycle()
        // Set by [navigateToHold] for a hold just placed (M3-M7); kept in the entry's saved state so the prompt is
        // offered once per placement, not again on a configuration change.
        val promptPending by entry.savedStateHandle.getStateFlow(REMINDER_PROMPT_KEY, false).collectAsStateWithLifecycle()
        BahrDestination(this) {
            HoldScreen(
                hold = entry.toRoute<HoldRoute>(),
                onEnded = onEnded,
                alreadyHeldNotice = alreadyHeld,
                onAlreadyHeldNoticeSeen = { entry.savedStateHandle[ALREADY_HELD_KEY] = false },
                reminderPrompt = reminderPrompt?.takeIf { promptPending },
                onReminderPromptDone = { entry.savedStateHandle[REMINDER_PROMPT_KEY] = false },
            )
        }
    }
}

/**
 * Between a tap on the hold reminder and the held seats (M3-M7, [navigateToHoldByRef]): [onOpen] gets the live hold
 * (wire it to [openHeldSeats]); [onEnded] is called when it is over, once the app has said so (wire it to a plain pop).
 */
fun NavGraphBuilder.openHoldScreen(
    onOpen: (HoldRoute) -> Unit,
    onEnded: () -> Unit,
    onBack: () -> Unit,
) {
    composable<OpenHoldRoute> { entry ->
        BahrDestination(this) {
            OpenHoldScreen(ref = entry.toRoute<OpenHoldRoute>().ref, onOpen = onOpen, onEnded = onEnded, onBack = onBack)
        }
    }
}

/**
 * A tap on the hold reminder (M3-M7): the held seats of [ref]. Already on the back stack (the person was booking in
 * this tab) → back to them, which re-reads the deadline as it shows; otherwise [OpenHoldRoute] on top of what is open,
 * which reads the hold again and hands over to the held seats.
 */
fun NavController.navigateToHoldByRef(ref: String) {
    // The topmost held-seats entry (getBackStackEntry), if any.
    val held = if (hasEntry<HoldRoute>()) getBackStackEntry<HoldRoute>().toRoute<HoldRoute>() else null
    if (held?.ref == ref) {
        popBackStack<HoldRoute>(inclusive = false)
    } else {
        navigate(OpenHoldRoute(ref)) { launchSingleTop = true }
    }
}

/** [openHoldScreen]'s live hold: the held seats take its place, so Back from them returns to where the tap landed. */
fun NavController.openHeldSeats(hold: HoldRoute) =
    navigate(hold) {
        popUpTo<OpenHoldRoute> { inclusive = true }
        launchSingleTop = true
    }

fun NavGraphBuilder.bookingConfirmedScreen(onDone: () -> Unit) {
    composable<BookingConfirmedRoute> { entry ->
        BahrDestination(this) { BookingConfirmedScreen(ref = entry.toRoute<BookingConfirmedRoute>().ref, onDone = onDone) }
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
 *
 * [alreadyHeld]: the hold is the device's existing one, not the one this date + party was about to
 * place. That date + party leaves the stack (it belongs to another booking) and the hold screen
 * shows the "already held" notice. Also how Home's "Continue your booking" card opens the hold, with
 * [alreadyHeld] false; single-top, so a double tap does not stack it twice.
 */
fun NavController.navigateToHold(
    hold: HoldRoute,
    alreadyHeld: Boolean = false,
) {
    navigate(hold) {
        if (alreadyHeld) popUpTo<BookingRoute> { inclusive = true }
        launchSingleTop = true
    }
    val entry = currentBackStackEntry?.savedStateHandle ?: return
    if (alreadyHeld) {
        entry[ALREADY_HELD_KEY] = true
    } else if (previousBackStackEntry?.destination?.hasRoute<BookingRoute>() == true) {
        // Just placed from date + party (M3-M7): offer the reminder's pre-prompt once. Not for Home's card, a
        // reminder's tap or a hold handed back, which reopen a hold the person has already been asked about.
        entry[REMINDER_PROMPT_KEY] = true
    }
}

/**
 * Back from the held seats to date + party under them (HANDOFF: at 00:00 the user goes back to date
 * selection), telling it whether the hold ran out so it can say so; it re-reads its dates either way.
 *
 * Pops up to the topmost held-seats entry rather than "one back": if a trip opened by a link sits
 * above the hold, the hold is not what plain `popBackStack()` would remove. Only the held-seats screen
 * calls this, and only while it is showing. The entry under it is usually its date + party; when the
 * hold was opened from Home's "Continue your booking" card (or handed back instead of a second hold)
 * it is Home or a trip page, which ignore the key, so the user lands there.
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
internal const val REMINDER_PROMPT_KEY = "hold_reminder_prompt"
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

/**
 * A booking opened from outside the booking flow (M3-M6: a notification about it), on top of what is open, by its
 * reference: the same page as after paying, which reads the booking again and shows it as it is now. Unlike
 * [navigateToBookingConfirmed] it pops nothing, so Back returns to where it was opened from.
 */
fun NavController.navigateToBookingByRef(ref: String) = navigate(BookingConfirmedRoute(ref)) { launchSingleTop = true }
