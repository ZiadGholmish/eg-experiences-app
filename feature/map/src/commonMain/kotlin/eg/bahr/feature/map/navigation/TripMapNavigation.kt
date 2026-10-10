package eg.bahr.feature.map.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.bahrBackExit
import eg.bahr.core.designsystem.theme.bahrForwardEnter
import eg.bahr.feature.map.presentation.TripMapScreen
import kotlinx.serialization.Serializable

/** The map (M4-M2), opened from the "Map" pill in Home's "Trips" title row. */
@Serializable
data object TripMapRoute

/**
 * The map feature's public surface. A trip opened from a pin goes to [onTripClick] (the app wires it
 * to the trips feature); Back and the bar's "List" go to [onBack].
 *
 * M4-M6's transition: the map is only ever opened from Home, so it always comes in with the shared
 * slide + fade and leaves the same way on Back. Home does its half when the app tells `tripListScreen`
 * that this route opens from it ([isTripMap]). To and from a trip the NavHost's own transition applies.
 * [reducedMotion] is read when a transition starts; with it on the screens change at once.
 */
fun NavGraphBuilder.tripMapScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    reducedMotion: () -> Boolean = { false },
) {
    composable<TripMapRoute>(
        enterTransition = { bahrForwardEnter(reducedMotion()) },
        popExitTransition = { bahrBackExit(reducedMotion()) },
    ) {
        TripMapScreen(onBack = onBack, onTripClick = onTripClick)
    }
}

/** Whether [this] entry is the map: lets Home slide aside for it like for its own pages. */
fun NavBackStackEntry.isTripMap(): Boolean = destination.hasRoute<TripMapRoute>()

fun NavController.navigateToTripMap(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(TripMapRoute, builder)
