package eg.bahr.feature.trips.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.feature.trips.presentation.TripDetailScreen
import eg.bahr.feature.trips.presentation.TripListScreen

/**
 * The trips feature's public surface: destinations for the app's NavHost, and how to reach them.
 * What happens *after* a trips screen (booking) is the caller's lambda, so this feature never
 * imports another feature.
 */
fun NavGraphBuilder.tripListScreen(onTripClick: (slug: String) -> Unit) {
    composable<TripListRoute> {
        TripListScreen(onTripClick = onTripClick)
    }
}

fun NavGraphBuilder.tripDetailScreen(onContinue: (departureId: Long) -> Unit) {
    composable<TripDetailRoute> { entry ->
        TripDetailScreen(slug = entry.toRoute<TripDetailRoute>().slug, onContinue = onContinue)
    }
}

fun NavController.navigateToTripList(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(TripListRoute, builder)

fun NavController.navigateToTripDetail(slug: String) = navigate(TripDetailRoute(slug))
