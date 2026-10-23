package eg.bahr.feature.notifications.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.notifications.presentation.NotificationsScreen
import kotlinx.serialization.Serializable

/**
 * The notifications page (M3-M0b as the Alerts tab's root; since M3-M6a a page pushed from Home's bell,
 * user 2026-10-23).
 */
@Serializable
data object NotificationsRoute

/**
 * The notifications page. [onBack] is its top bar's Back (system Back and the edge swipe pop it too).
 * [signInButtons] draws feature:auth's sign-in buttons for the signed-out state (wired in :composeApp,
 * given a modifier for its place).
 */
fun NavGraphBuilder.notificationsScreen(
    onBack: () -> Unit,
    signInButtons: @Composable (Modifier) -> Unit,
) {
    composable<NotificationsRoute> {
        BahrDestination(this) { NotificationsScreen(onBack = onBack, signInButtons = signInButtons) }
    }
}

/** Opens the notifications page over what is on screen; a second tap while it is on top adds none. */
fun NavController.navigateToNotifications() = navigate(NotificationsRoute) { launchSingleTop = true }
