package eg.bahr.feature.hostworkspace.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.hostworkspace.presentation.HostDepartureScreen
import eg.bahr.feature.hostworkspace.presentation.HostModeScreen
import kotlinx.serialization.Serializable

/**
 * Host mode (M5-M1): its sign-in while host mode's own session is signed out, else its shell with its own bar
 * (Departures · Profile). Pushed from Profile ("I'm a host"), so the traveller's bar hides under it.
 */
@Serializable
data object HostModeRoute

/** One of the host's dates: the manifest, broadcast and manual bookings. */
@Serializable
data class HostDepartureRoute(
    val departureId: String,
)

/** [onExit] goes back to the traveller app (usually a pop); [onOpenDeparture] opens a date ([navigateToHostDeparture]). */
fun NavGraphBuilder.hostModeScreen(
    onExit: () -> Unit,
    onOpenDeparture: (departureId: String) -> Unit,
) {
    composable<HostModeRoute> {
        BahrDestination(this) { HostModeScreen(onExit = onExit, onOpenDeparture = onOpenDeparture) }
    }
}

/** [onSignedOut]: host mode's sign-in ended while the date was open; usually [popToHostMode], which then shows the sign-in. */
fun NavGraphBuilder.hostDepartureScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    composable<HostDepartureRoute> { entry ->
        val route = entry.toRoute<HostDepartureRoute>()
        BahrDestination(this) { HostDepartureScreen(departureId = route.departureId, onBack = onBack, onSignedOut = onSignedOut) }
    }
}

fun NavController.navigateToHostMode() = navigate(HostModeRoute) { launchSingleTop = true }

fun NavController.navigateToHostDeparture(departureId: String) = navigate(HostDepartureRoute(departureId)) { launchSingleTop = true }

/** Back to host mode's shell (its sign-in when signed out); a stack without it just goes back once. */
fun NavController.popToHostMode() {
    if (!popBackStack<HostModeRoute>(inclusive = false)) popBackStack()
}
