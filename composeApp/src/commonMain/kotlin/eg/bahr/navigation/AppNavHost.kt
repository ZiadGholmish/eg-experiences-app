package eg.bahr.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import eg.bahr.feature.booking.navigation.BookingConfirmedRoute
import eg.bahr.feature.booking.navigation.BookingRoute
import eg.bahr.feature.booking.presentation.BookingConfirmedScreen
import eg.bahr.feature.booking.presentation.BookingScreen
import eg.bahr.feature.splash.SplashScreen
import eg.bahr.feature.splash.navigation.SplashRoute
import eg.bahr.feature.trips.navigation.TripDetailRoute
import eg.bahr.feature.trips.navigation.TripListRoute
import eg.bahr.feature.trips.presentation.TripDetailScreen
import eg.bahr.feature.trips.presentation.TripListScreen

/**
 * The whole graph, in one place.
 *
 * Routes are declared by each feature in its own `navigation` package and wired
 * here, so a feature never has to know what comes after it — the argument
 * types are shared, the destinations are not.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = SplashRoute) {
        composable<SplashRoute> {
            SplashScreen(
                onReady = {
                    navController.navigate(TripListRoute) {
                        // Splash must not be reachable with the back button.
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                },
            )
        }

        composable<TripListRoute> {
            TripListScreen(
                onTripClick = { slug -> navController.navigate(TripDetailRoute(slug)) },
            )
        }

        composable<TripDetailRoute> { entry ->
            val route: TripDetailRoute = entry.toRoute()
            TripDetailScreen(
                slug = route.slug,
                onContinue = { departureId -> navController.navigate(BookingRoute(departureId)) },
            )
        }

        composable<BookingRoute> { entry ->
            val route: BookingRoute = entry.toRoute()
            BookingScreen(
                departureId = route.departureId,
                onBooked = { ref ->
                    navController.navigate(BookingConfirmedRoute(ref)) {
                        // The hold screen is finished once seats are held;
                        // going "back" into it would place a second hold.
                        popUpTo(BookingRoute(route.departureId)) { inclusive = true }
                    }
                },
                onHoldExpired = { navController.popBackStack() },
            )
        }

        composable<BookingConfirmedRoute> { entry ->
            val route: BookingConfirmedRoute = entry.toRoute()
            BookingConfirmedScreen(
                ref = route.ref,
                onDone = {
                    navController.navigate(TripListRoute) {
                        popUpTo(TripListRoute) { inclusive = true }
                    }
                },
            )
        }
    }
}
