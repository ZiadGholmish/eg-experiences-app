package eg.bahr.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.ProvideBahrNavScope
import eg.bahr.core.designsystem.theme.bahrBackEnter
import eg.bahr.core.designsystem.theme.bahrForwardExit
import eg.bahr.feature.home.presentation.TripListScreen

/**
 * The home feature's public surface: Home's destination for the app's NavHost, and how to reach it.
 * Everything Home opens (a trip, a category page, a row's "See all", search, the map) belongs to other
 * features, so it goes out through the lambdas below and the app decides where (M4-M1c).
 *
 * [header] is drawn under the list's title (and at the top of its loading, error and empty
 * views); the app fills it from another feature (M2-M4: booking's "Continue your booking" card)
 * without this feature knowing what it is. It is handed `gap`, the space above it, as a modifier to
 * put on its content inside its own enter/exit animation, so the space comes and goes with it
 * (M4-M4). It must draw nothing when it has nothing to show, gap included.
 *
 * [onHomeAction] is where a Home banner, a category chip or a row's "See all" goes (M4-M1a, M4-M1b):
 * the app decides what a trip, a category, a section list or a link opens. A trip card in a Home row
 * goes to [onTripClick]. [onSearch] is the search entry under the title (M4-M3).
 *
 * [onMap] (M4-M2) puts the "Map" pill beside the title; the app opens another feature's screen with
 * it.
 *
 * M4-M6: Home steps aside with the shared slide + fade for the pages it opens, and comes back the same
 * way on Back; other destinations keep the NavHost's transition. Those pages are other features', so
 * [opensFromHome] names them (feature:trips' category page, "See all" and search, feature:map's map).
 * [reducedMotion] is read when a transition starts (the platform setting can change while the app
 * runs); with it on the screens change at once.
 */
fun NavGraphBuilder.tripListScreen(
    onTripClick: (slug: String) -> Unit,
    opensFromHome: (NavBackStackEntry) -> Boolean,
    header: @Composable (gap: Modifier) -> Unit = {},
    onHomeAction: (HomeAction) -> Unit = {},
    onSearch: () -> Unit = {},
    onMap: (() -> Unit)? = null,
    reducedMotion: () -> Boolean = { false },
) {
    composable<TripListRoute>(
        exitTransition = { if (opensFromHome(targetState)) bahrForwardExit(reducedMotion()) else null },
        popEnterTransition = { if (opensFromHome(initialState)) bahrBackEnter(reducedMotion()) else null },
    ) {
        ProvideBahrNavScope(this) {
            TripListScreen(onTripClick = onTripClick, header = header, onAction = onHomeAction, onSearch = onSearch, onMap = onMap)
        }
    }
}

/** Home: the other features' pages opened from it slide in only when it is what they leave (M4-M6). */
fun NavBackStackEntry.isHome(): Boolean = destination.hasRoute<TripListRoute>()

fun NavController.navigateToTripList(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(TripListRoute, builder)
