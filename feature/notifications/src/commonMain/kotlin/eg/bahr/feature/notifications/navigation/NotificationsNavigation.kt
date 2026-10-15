package eg.bahr.feature.notifications.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.notifications.presentation.NotificationsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/** The Alerts tab's root (M3-M0b). */
@Serializable
data object NotificationsRoute

/**
 * The Alerts tab. [signInButtons] draws feature:auth's sign-in buttons for the signed-out state (wired in
 * :composeApp, given a modifier for its place). [scrollToTop] fires when the selected tab is tapped again.
 */
fun NavGraphBuilder.notificationsScreen(
    signInButtons: @Composable (Modifier) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    composable<NotificationsRoute> {
        BahrDestination(this) { NotificationsScreen(signInButtons = signInButtons, scrollToTop = scrollToTop) }
    }
}
