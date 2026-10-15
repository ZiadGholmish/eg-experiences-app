package eg.bahr.feature.map.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.map.presentation.TripMapScreen
import kotlinx.serialization.Serializable

/** The map (M4-M2); since M4-M8 the Map tab's root (Home's "Map" pill switches to that tab). */
@Serializable
data object TripMapRoute

/**
 * The map feature's public surface. A trip opened from a pin goes to [onTripClick] (the app wires it
 * to the trips feature); Back goes to [onBack] (null: no Back, as the Map tab's root, M4-M8) and the
 * bar's "List" to [onList]. [onSearch] (M3-M0b) is the header's search button: the full-screen search,
 * feature:trips', wired in :composeApp.
 *
 * M3-M12: it moves with the app's one screen transition, which the NavHost sets, on an opaque page.
 */
fun NavGraphBuilder.tripMapScreen(
    onBack: (() -> Unit)?,
    onTripClick: (slug: String) -> Unit,
    onList: () -> Unit = { onBack?.invoke() },
    onSearch: (() -> Unit)? = null,
) {
    composable<TripMapRoute> {
        BahrDestination(this) {
            TripMapScreen(onBack = onBack, onTripClick = onTripClick, onList = onList, onSearch = onSearch)
        }
    }
}

fun NavController.navigateToTripMap(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(TripMapRoute, builder)
