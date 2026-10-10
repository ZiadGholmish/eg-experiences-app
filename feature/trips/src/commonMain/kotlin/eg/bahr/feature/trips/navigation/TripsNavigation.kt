package eg.bahr.feature.trips.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.ProvideBahrNavScope
import eg.bahr.core.designsystem.theme.bahrBackEnter
import eg.bahr.core.designsystem.theme.bahrBackExit
import eg.bahr.core.designsystem.theme.bahrForwardEnter
import eg.bahr.core.designsystem.theme.bahrForwardExit
import eg.bahr.feature.trips.presentation.CategoryTripsScreen
import eg.bahr.feature.trips.presentation.SearchTripsScreen
import eg.bahr.feature.trips.presentation.SectionTripsScreen
import eg.bahr.feature.trips.presentation.TripDetailScreen
import eg.bahr.feature.trips.presentation.TripListScreen

/**
 * The trips feature's public surface: destinations for the app's NavHost, and how to reach them.
 * What happens *after* a trips screen (booking) is the caller's lambda, so this feature never
 * imports another feature.
 *
 * Home. [header] is drawn under the list's title (and at the top of its loading, error and empty
 * views); the app fills it from another feature (M2-M4: booking's "Continue your booking" card)
 * without this feature knowing what it is. It is handed `gap`, the space above it, as a modifier to
 * put on its content inside its own enter/exit animation, so the space comes and goes with it
 * (M4-M4). It must draw nothing when it has nothing to show, gap included.
 *
 * [onHomeAction] is where a Home banner, a category chip or a row's "See all" goes (M4-M1a, M4-M1b):
 * the app decides what a trip, a category, a section list or a link opens. A trip card in a Home row
 * goes to [onTripClick]. [onSearch] is the search entry under the title (M4-M3).
 *
 * M4-M6: Home steps aside with the shared slide + fade when a category, "See all" or search opens
 * from it, and comes back the same way on Back; other destinations keep the NavHost's transition.
 * [reducedMotion] is read when a transition starts (the platform setting can change while the app
 * runs); with it on the screens change at once.
 */
fun NavGraphBuilder.tripListScreen(
    onTripClick: (slug: String) -> Unit,
    header: @Composable (gap: Modifier) -> Unit = {},
    onHomeAction: (HomeAction) -> Unit = {},
    onSearch: () -> Unit = {},
    reducedMotion: () -> Boolean = { false },
) {
    composable<TripListRoute>(
        exitTransition = { if (targetState.opensFromHome()) bahrForwardExit(reducedMotion()) else null },
        popEnterTransition = { if (initialState.opensFromHome()) bahrBackEnter(reducedMotion()) else null },
    ) {
        ProvideBahrNavScope(this) {
            TripListScreen(onTripClick = onTripClick, header = header, onAction = onHomeAction, onSearch = onSearch)
        }
    }
}

/**
 * [onContinue] gets the trip and the picked date (a UUID string, as the contract has it); it is only
 * called for a bookable date.
 */
fun NavGraphBuilder.tripDetailScreen(
    onBack: () -> Unit,
    onContinue: (slug: String, departureId: String) -> Unit,
) {
    composable<TripDetailRoute> { entry ->
        TripDetailScreen(slug = entry.toRoute<TripDetailRoute>().slug, onBack = onBack, onContinue = onContinue)
    }
}

/**
 * The category page (M4-M1b). Back is [onBack]; Home under it keeps its scroll position, because its
 * list state is saved with its back-stack entry.
 */
fun NavGraphBuilder.categoryTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    reducedMotion: () -> Boolean = { false },
) {
    openedFromHome<CategoryTripsRoute>(reducedMotion) { entry ->
        val route = entry.toRoute<CategoryTripsRoute>()
        CategoryTripsScreen(
            category = route.category,
            title = route.title,
            onBack = onBack,
            onTripClick = onTripClick,
            sharedKey = route.sharedKey,
        )
    }
}

/** A Home row's "See all" list (M4-M1b). */
fun NavGraphBuilder.sectionTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    reducedMotion: () -> Boolean = { false },
) {
    openedFromHome<SectionTripsRoute>(reducedMotion) { entry ->
        val route = entry.toRoute<SectionTripsRoute>()
        SectionTripsScreen(
            sectionId = route.sectionId,
            title = route.title,
            onBack = onBack,
            onTripClick = onTripClick,
            sharedKey = route.sharedKey,
        )
    }
}

/**
 * Search (M4-M3). Back is [onBack]; the search and its results are kept while a trip opened from
 * them is on top (the view model lives with this back-stack entry).
 */
fun NavGraphBuilder.searchTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    reducedMotion: () -> Boolean = { false },
) {
    openedFromHome<SearchTripsRoute>(reducedMotion) {
        SearchTripsScreen(onBack = onBack, onTripClick = onTripClick)
    }
}

/**
 * A page opened from Home (M4-M6): it slides in from the end side with a fade, and leaves the same way
 * on Back to Home. To and from any other page (a trip it opens) the NavHost's own transition applies,
 * so the trip page and booking flow are unchanged. Its AnimatedVisibilityScope is handed down for the
 * header morph (`bahrSharedBounds`).
 */
private inline fun <reified T : Any> NavGraphBuilder.openedFromHome(
    noinline reducedMotion: () -> Boolean,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = { if (initialState.isHome()) bahrForwardEnter(reducedMotion()) else null },
        popExitTransition = { if (targetState.isHome()) bahrBackExit(reducedMotion()) else null },
    ) { entry ->
        ProvideBahrNavScope(this) { content(entry) }
    }
}

private fun NavBackStackEntry.isHome(): Boolean = destination.hasRoute<TripListRoute>()

/** The pages Home opens with the shared transition. */
private fun NavBackStackEntry.opensFromHome(): Boolean =
    destination.hasRoute<CategoryTripsRoute>() || destination.hasRoute<SectionTripsRoute>() || destination.hasRoute<SearchTripsRoute>()

fun NavController.navigateToSearchTrips(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(SearchTripsRoute, builder)

/** [sharedKey]: the Home element that morphs into the page's header ([HomeAction.OpenCategory.sharedKey]). */
fun NavController.navigateToCategoryTrips(
    category: String,
    title: String? = null,
    sharedKey: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(CategoryTripsRoute(category, title, sharedKey), builder)

/** [sharedKey]: the Home row title that morphs into the page's heading ([HomeAction.OpenSection.sharedKey]). */
fun NavController.navigateToSectionTrips(
    sectionId: String,
    title: String? = null,
    sharedKey: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(SectionTripsRoute(sectionId, title, sharedKey), builder)

fun NavController.navigateToTripList(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(TripListRoute, builder)

fun NavController.navigateToTripDetail(
    slug: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(TripDetailRoute(slug), builder)
