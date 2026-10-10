package eg.bahr.feature.trips.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.feature.trips.presentation.CategoryTripsScreen
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
 * without this feature knowing what it is. It must draw nothing when it has nothing to show.
 *
 * [onHomeAction] is where a Home banner, a category chip or a row's "See all" goes (M4-M1a, M4-M1b):
 * the app decides what a trip, a category, a section list or a link opens. A trip card in a Home row
 * goes to [onTripClick].
 */
fun NavGraphBuilder.tripListScreen(
    onTripClick: (slug: String) -> Unit,
    header: @Composable () -> Unit = {},
    onHomeAction: (HomeAction) -> Unit = {},
) {
    composable<TripListRoute> {
        TripListScreen(onTripClick = onTripClick, header = header, onAction = onHomeAction)
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
) {
    composable<CategoryTripsRoute> { entry ->
        val route = entry.toRoute<CategoryTripsRoute>()
        CategoryTripsScreen(category = route.category, title = route.title, onBack = onBack, onTripClick = onTripClick)
    }
}

/** A Home row's "See all" list (M4-M1b). */
fun NavGraphBuilder.sectionTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
) {
    composable<SectionTripsRoute> { entry ->
        val route = entry.toRoute<SectionTripsRoute>()
        SectionTripsScreen(sectionId = route.sectionId, title = route.title, onBack = onBack, onTripClick = onTripClick)
    }
}

fun NavController.navigateToCategoryTrips(
    category: String,
    title: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(CategoryTripsRoute(category, title), builder)

fun NavController.navigateToSectionTrips(
    sectionId: String,
    title: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(SectionTripsRoute(sectionId, title), builder)

fun NavController.navigateToTripList(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(TripListRoute, builder)

fun NavController.navigateToTripDetail(
    slug: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(TripDetailRoute(slug), builder)
