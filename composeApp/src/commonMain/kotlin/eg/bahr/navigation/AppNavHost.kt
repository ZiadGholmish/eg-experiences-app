package eg.bahr.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.deeplink.DeepLinkDestinations
import eg.bahr.deeplink.DeepLinkInbox
import eg.bahr.deeplink.DeepLinkParser
import eg.bahr.deeplink.TripLinkPlacement
import eg.bahr.deeplink.tripLinkPlacement
import eg.bahr.feature.booking.navigation.ContinueBookingSlot
import eg.bahr.feature.booking.navigation.bookingConfirmedScreen
import eg.bahr.feature.booking.navigation.bookingScreen
import eg.bahr.feature.booking.navigation.hasBookingInProgress
import eg.bahr.feature.booking.navigation.holdScreen
import eg.bahr.feature.booking.navigation.navigateToBooking
import eg.bahr.feature.booking.navigation.navigateToHold
import eg.bahr.feature.booking.navigation.returnToDateSelection
import eg.bahr.feature.splash.navigation.SplashRoute
import eg.bahr.feature.splash.navigation.splashScreen
import eg.bahr.feature.trips.navigation.TripListRoute
import eg.bahr.feature.trips.navigation.navigateToTripDetail
import eg.bahr.feature.trips.navigation.navigateToTripList
import eg.bahr.feature.trips.navigation.tripDetailScreen
import eg.bahr.feature.trips.navigation.tripListScreen
import org.koin.compose.koinInject

/**
 * The whole graph, in one place.
 *
 * Each feature publishes its destinations (`xScreen`) and entry points (`navigateToX`) from its
 * own `navigation` package; this is where they are joined up, so a feature never has to know
 * what comes after it. Screens stay `internal` to their feature.
 *
 * Deep links (App Links, Universal Links) arrive in [AppDeepLinkInbox] and are opened here once the
 * splash screen has handed over to the list, on a cold start and a warm one alike.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    // Opens the pending deep link. It waits while splash is showing, because splash replaces itself
    // with the list when it finishes and a linked trip must land on top of that list. Inline rather
    // than a helper composable: :composeApp holds no composable besides App and AppNavHost.
    val inbox: DeepLinkInbox = AppDeepLinkInbox
    val parser: DeepLinkParser = koinInject()
    val pendingLink by inbox.pending.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val pastSplash = entry?.destination?.let { !it.hasRoute<SplashRoute>() } ?: false
    val destinations = remember(navController) { NavControllerDeepLinkDestinations(navController) }
    LaunchedEffect(pendingLink, pastSplash) {
        if (pastSplash) inbox.dispatch(parser, destinations)
    }

    NavHost(navController = navController, startDestination = SplashRoute) {
        splashScreen(
            onReady = {
                navController.navigateToTripList {
                    // Splash must not be reachable with the back button.
                    popUpTo<SplashRoute> { inclusive = true }
                }
            },
        )

        tripListScreen(
            onTripClick = navController::navigateToTripDetail,
            // A live seat hold (survives a restart) sits at the top of Home; tap → the held seats.
            header = { ContinueBookingSlot(onOpen = { navController.navigateToHold(it) }) },
        )

        tripDetailScreen(
            onBack = { navController.popBackStack() },
            onContinue = navController::navigateToBooking,
        )

        bookingScreen(
            onBack = { navController.popBackStack() },
            onHeld = navController::navigateToHold,
        )

        // Released or run out: back to date + party under it, which re-reads its dates.
        holdScreen(onEnded = navController::returnToDateSelection)

        bookingConfirmedScreen(
            onDone = {
                navController.navigateToTripList {
                    popUpTo<TripListRoute> { inclusive = true }
                }
            },
        )
    }
}

private class NavControllerDeepLinkDestinations(
    private val navController: NavHostController,
) : DeepLinkDestinations {
    override fun openTrip(slug: String) {
        val placement = tripLinkPlacement(bookingInProgress = navController.hasBookingInProgress())
        navController.navigateToTripDetail(slug) {
            // Otherwise whatever was open goes and the list stays: back from the linked trip is the list.
            if (placement == TripLinkPlacement.AboveList) popUpTo<TripListRoute>()
            // The same trip already on top is not pushed twice.
            launchSingleTop = true
        }
    }

    override fun openList() {
        if (!navController.popBackStack<TripListRoute>(inclusive = false)) {
            navController.navigateToTripList { launchSingleTop = true }
        }
    }
}
