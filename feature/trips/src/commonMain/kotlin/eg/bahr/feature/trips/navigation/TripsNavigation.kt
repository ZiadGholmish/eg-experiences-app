package eg.bahr.feature.trips.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.ProvideBahrNavScope
import eg.bahr.core.designsystem.theme.bahrBackExit
import eg.bahr.core.designsystem.theme.bahrForwardEnter
import eg.bahr.feature.trips.presentation.CategoryTripsScreen
import eg.bahr.feature.trips.presentation.SearchTripsScreen
import eg.bahr.feature.trips.presentation.SectionTripsScreen
import eg.bahr.feature.trips.presentation.TripDetailScreen

/*
 * The trips feature's public surface: destinations for the app's NavHost, and how to reach them.
 * What happens *after* a trips screen (booking) is the caller's lambda, so this feature never
 * imports another feature. Home is feature:home's (M4-M1c).
 *
 * M4-M6: the category page, a row's "See all" and search slide in with the shared slide + fade when
 * opened from Home, and leave the same way on Back to it. This feature cannot name Home's route, so
 * each takes `isHome`, which the app answers with feature:home's `isHome()`; Home in turn asks
 * `isOpenedFromHome` which of its destinations get that transition.
 */

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
    isHome: (NavBackStackEntry) -> Boolean,
    reducedMotion: () -> Boolean = { false },
) {
    openedFromHome<CategoryTripsRoute>(isHome, reducedMotion) { entry ->
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
    isHome: (NavBackStackEntry) -> Boolean,
    reducedMotion: () -> Boolean = { false },
) {
    openedFromHome<SectionTripsRoute>(isHome, reducedMotion) { entry ->
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
    isHome: (NavBackStackEntry) -> Boolean,
    reducedMotion: () -> Boolean = { false },
) {
    openedFromHome<SearchTripsRoute>(isHome, reducedMotion) {
        SearchTripsScreen(onBack = onBack, onTripClick = onTripClick)
    }
}

/**
 * A page opened from Home (M4-M6): it slides in from the end side with a fade, and leaves the same way
 * on Back to Home ([isHome]). To and from any other page (a trip it opens) the NavHost's own transition applies,
 * so the trip page and booking flow are unchanged. Its AnimatedVisibilityScope is handed down for the
 * header morph (`bahrSharedBounds`).
 */
private inline fun <reified T : Any> NavGraphBuilder.openedFromHome(
    noinline isHome: (NavBackStackEntry) -> Boolean,
    noinline reducedMotion: () -> Boolean,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = { if (isHome(initialState)) bahrForwardEnter(reducedMotion()) else null },
        popExitTransition = { if (isHome(targetState)) bahrBackExit(reducedMotion()) else null },
    ) { entry ->
        ProvideBahrNavScope(this) { content(entry) }
    }
}

/**
 * The pages of this feature Home opens with the shared transition: the category page, a row's "See
 * all" and search. The app hands it to Home's `opensFromHome` (M4-M1c: Home cannot name these routes).
 */
fun NavBackStackEntry.isOpenedFromHome(): Boolean =
    destination.hasRoute<CategoryTripsRoute>() || destination.hasRoute<SectionTripsRoute>() || destination.hasRoute<SearchTripsRoute>()

fun NavController.navigateToSearchTrips(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(SearchTripsRoute, builder)

/** [sharedKey]: the Home element that morphs into the page's header (feature:home's `HomeAction.OpenCategory.sharedKey`). */
fun NavController.navigateToCategoryTrips(
    category: String,
    title: String? = null,
    sharedKey: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(CategoryTripsRoute(category, title, sharedKey), builder)

/** [sharedKey]: the Home row title that morphs into the page's heading (feature:home's `HomeAction.OpenSection.sharedKey`). */
fun NavController.navigateToSectionTrips(
    sectionId: String,
    title: String? = null,
    sharedKey: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(SectionTripsRoute(sectionId, title, sharedKey), builder)

fun NavController.navigateToTripDetail(
    slug: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(TripDetailRoute(slug), builder)
