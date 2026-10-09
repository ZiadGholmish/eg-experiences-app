package eg.bahr.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import eg.bahr.feature.booking.navigation.bookingConfirmedScreen
import eg.bahr.feature.booking.navigation.bookingScreen
import eg.bahr.feature.booking.navigation.navigateToBookingConfirmed
import eg.bahr.feature.splash.navigation.SplashRoute
import eg.bahr.feature.splash.navigation.splashScreen
import eg.bahr.feature.trips.navigation.TripListRoute
import eg.bahr.feature.trips.navigation.navigateToTripDetail
import eg.bahr.feature.trips.navigation.navigateToTripList
import eg.bahr.feature.trips.navigation.tripDetailScreen
import eg.bahr.feature.trips.navigation.tripListScreen

/**
 * The whole graph, in one place.
 *
 * Each feature publishes its destinations (`xScreen`) and entry points (`navigateToX`) from its
 * own `navigation` package; this is where they are joined up, so a feature never has to know
 * what comes after it. Screens stay `internal` to their feature.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = SplashRoute) {
        splashScreen(
            onReady = {
                navController.navigateToTripList {
                    // Splash must not be reachable with the back button.
                    popUpTo<SplashRoute> { inclusive = true }
                }
            },
        )

        tripListScreen(onTripClick = navController::navigateToTripDetail)

        tripDetailScreen(
            onBack = { navController.popBackStack() },
            // Intentionally a no-op until M2: the date + party screen is M2-M1, and the existing
            // BookingRoute still takes the Java-era `Long` departure id, not the contract's UUID.
            onContinue = { _, _ -> },
        )

        bookingScreen(
            onBooked = navController::navigateToBookingConfirmed,
            onHoldExpired = { navController.popBackStack() },
        )

        bookingConfirmedScreen(
            onDone = {
                navController.navigateToTripList {
                    popUpTo<TripListRoute> { inclusive = true }
                }
            },
        )
    }
}
