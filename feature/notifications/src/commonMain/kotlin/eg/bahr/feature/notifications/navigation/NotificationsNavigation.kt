package eg.bahr.feature.notifications.navigation

import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.notifications.presentation.AlertDestinations
import eg.bahr.feature.notifications.presentation.NotificationsScreen
import kotlinx.serialization.Serializable

/**
 * The notifications page (M3-M0b as the Alerts tab's root; since M3-M6a a page pushed from Home's bell,
 * user 2026-10-23).
 */
@Serializable
data object NotificationsRoute

/**
 * The notifications page (M3-M6): the account's inbox, or this install's guest inbox while signed out. [onBack] is its
 * top bar's Back (system Back and the edge swipe pop it too). A tap on an item reads it and opens its page, in this
 * tab's stack: [onOpenTrip] the trip page (on `date` with it picked when the item names one; `placeOpened` for "a place
 * opened", which the page checks is still true), [onOpenBooking] the booking by reference, [onOpenReview] My reviews
 * (null until that page exists: the item then only reads).
 */
fun NavGraphBuilder.notificationsScreen(
    onBack: () -> Unit,
    onOpenTrip: (slug: String, date: String?, placeOpened: Boolean) -> Unit,
    onOpenBooking: (ref: String) -> Unit,
    onOpenReview: ((id: String) -> Unit)? = null,
) {
    composable<NotificationsRoute> {
        BahrDestination(this) {
            val destinations =
                remember(onOpenTrip, onOpenBooking, onOpenReview) { AlertDestinations(onOpenTrip, onOpenBooking, onOpenReview) }
            NotificationsScreen(onBack = onBack, destinations = destinations)
        }
    }
}

/** Opens the notifications page over what is on screen; a second tap while it is on top adds none. */
fun NavController.navigateToNotifications() = navigate(NotificationsRoute) { launchSingleTop = true }
