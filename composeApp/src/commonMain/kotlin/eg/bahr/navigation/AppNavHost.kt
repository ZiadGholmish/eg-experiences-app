package eg.bahr.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrSharedTransitions
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.deeplink.DeepLinkDestinations
import eg.bahr.deeplink.DeepLinkInbox
import eg.bahr.deeplink.DeepLinkParser
import eg.bahr.deeplink.TripLinkPlacement
import eg.bahr.deeplink.tripLinkPlacement
import eg.bahr.di.AppLanguageState
import eg.bahr.feature.booking.navigation.ContinueBookingSlot
import eg.bahr.feature.booking.navigation.bookingConfirmedScreen
import eg.bahr.feature.booking.navigation.bookingScreen
import eg.bahr.feature.booking.navigation.hasBookingInProgress
import eg.bahr.feature.booking.navigation.holdScreen
import eg.bahr.feature.booking.navigation.navigateToBooking
import eg.bahr.feature.booking.navigation.navigateToHold
import eg.bahr.feature.booking.navigation.returnToDateSelection
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.navigation.TripListRoute
import eg.bahr.feature.home.navigation.isHome
import eg.bahr.feature.home.navigation.navigateToTripList
import eg.bahr.feature.home.navigation.tripListScreen
import eg.bahr.feature.map.navigation.TripMapRoute
import eg.bahr.feature.map.navigation.tripMapScreen
import eg.bahr.feature.settings.navigation.SettingsRoute
import eg.bahr.feature.settings.navigation.settingsScreen
import eg.bahr.feature.splash.navigation.SplashRoute
import eg.bahr.feature.splash.navigation.splashScreen
import eg.bahr.feature.trips.navigation.SearchTripsRoute
import eg.bahr.feature.trips.navigation.categoryTripsScreen
import eg.bahr.feature.trips.navigation.hostProfileScreen
import eg.bahr.feature.trips.navigation.isOpenedFromHome
import eg.bahr.feature.trips.navigation.navigateToCategoryTrips
import eg.bahr.feature.trips.navigation.navigateToHostProfile
import eg.bahr.feature.trips.navigation.navigateToSectionTrips
import eg.bahr.feature.trips.navigation.navigateToTripDetail
import eg.bahr.feature.trips.navigation.searchTripsScreen
import eg.bahr.feature.trips.navigation.sectionTripsScreen
import eg.bahr.feature.trips.navigation.tripDetailScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.compose.koinInject

/**
 * One tab's NavHost (M4-M8): the whole graph, in one place, started at [tab]'s root.
 *
 * Each feature publishes its destinations (`xScreen`) and entry points (`navigateToX`) from its own
 * `navigation` package; this is where they are joined up, so a feature never has to know what comes
 * after it. Screens stay `internal` to their feature.
 *
 * Every tab has the same graph and its own NavController, so each keeps its own back stack: a trip
 * opened from search stays in the Search tab while Home keeps its scroll. Only the start differs:
 * Home (after splash, once per process), search, the map, Settings. Android composes the selected
 * tab's NavHost under its bar ([AppTabs]); iOS hosts one per tab, each in its own Compose root under
 * SwiftUI's `TabView`.
 *
 * The NavHost reports to [tabs] whether its root is on top, which is what shows or hides the bar, and
 * takes re-taps of its tab from it: off the root, back to the root; on it, back to the top.
 *
 * Deep links (App Links, Universal Links) open in the Home tab's stack only (the inbox is one per
 * process, so a single tab must take them): they arrive in [AppDeepLinkInbox], the app switches to
 * Home, and this NavHost opens them once splash has handed over to Home.
 */
@Composable
fun AppNavHost(
    tab: AppTab,
    tabs: AppTabsState,
    appVersion: String,
    navController: NavHostController = rememberNavController(),
) {
    // Read by the screen transitions when they start, so a reduce-motion change applies at once (M4-M6).
    val reduced by rememberUpdatedState(BahrTheme.reducedMotion)
    val reducedMotion = remember { { reduced } }

    // Splash runs once per process; a NavHost rebuilt later (a language switch) starts on Home itself.
    val start: Any = remember(tab) { tab.startRoute(splashDone = tabs.splashDone) }

    // The bar shows on this tab's root only.
    val entry by navController.currentBackStackEntryAsState()
    val atRoot = entry?.destination?.let { tab.isRoot(it) }
    LaunchedEffect(atRoot) { atRoot?.let { tabs.reportAtRoot(tab, it) } }

    // Re-taps of this tab, and Home's search entry (Search only). Both reach the root's screen through
    // a conflated channel, which holds a signal until the screen collects it (a first visit).
    val scrollToTop = remember { Channel<Unit>(Channel.CONFLATED) }
    val scrollRequests = remember { scrollToTop.receiveAsFlow() }
    LaunchedEffect(navController, tabs) {
        tabs.reselected.filter { it == tab }.collect {
            when (AppTabsState.reselectAction(atRoot = navController.isAt(tab))) {
                AppTabsState.ReselectAction.PopToRoot -> navController.popBackStack(tab.rootRoute, inclusive = false)
                AppTabsState.ReselectAction.ScrollToTop -> scrollToTop.trySend(Unit)
            }
        }
    }
    val searchFocus = remember { Channel<Unit>(Channel.CONFLATED) }
    val searchFocusRequests = remember { searchFocus.receiveAsFlow() }
    if (tab == AppTab.Search) {
        LaunchedEffect(navController, tabs) {
            tabs.searchFocusRequests().collect {
                navController.popBackStack(tab.rootRoute, inclusive = false)
                searchFocus.trySend(Unit)
            }
        }
    }

    // Opens the pending deep link (Home tab only). It waits while splash is showing, because splash
    // replaces itself with Home when it finishes and a linked trip must land on top of Home. A link
    // whose `?lang=` differs from the app's switches the language first and is opened by the NavHost
    // that switch rebuilds (DeepLinkInbox.dispatch). Inline rather than a helper composable: :composeApp
    // holds no composable besides the app roots, the tab host and the NavHost.
    if (tab == AppTab.Home) {
        val inbox: DeepLinkInbox = AppDeepLinkInbox
        val parser: DeepLinkParser = koinInject()
        val languages: AppLanguageState = koinInject()
        val pendingLink by inbox.pending.collectAsStateWithLifecycle()
        val pastSplash = entry?.destination?.let { !it.hasRoute<SplashRoute>() } ?: false
        val destinations = remember(navController, languages) { NavControllerDeepLinkDestinations(navController, languages) }
        LaunchedEffect(pendingLink, pastSplash) {
            if (pastSplash) inbox.dispatch(parser, destinations)
        }
    }

    // Shared elements (M4-M6): a Home chip or row title morphs into the page it opens.
    BahrSharedTransitions {
        NavHost(navController = navController, startDestination = start) {
            splashScreen(
                onReady = {
                    tabs.markSplashDone()
                    navController.navigateToTripList {
                        // Splash must not be reachable with the back button.
                        popUpTo<SplashRoute> { inclusive = true }
                    }
                },
            )

            tripListScreen(
                onTripClick = navController::navigateToTripDetail,
                // A live seat hold (survives a restart) sits at the top of Home; tap → the held seats.
                header = { gap -> ContinueBookingSlot(onOpen = { navController.navigateToHold(it) }, modifier = gap) },
                onHomeAction = { action ->
                    when (action) {
                        is HomeAction.OpenTrip -> navController.navigateToTripDetail(action.slug)
                        // A category chip, a `category` banner, or a category row's "See all" (M4-M1b).
                        is HomeAction.OpenCategory -> navController.navigateToCategoryTrips(action.key, action.title, action.sharedKey)
                        // Any other row's "See all": that row's whole list.
                        is HomeAction.OpenSection -> navController.navigateToSectionTrips(action.sectionId, action.title, action.sharedKey)
                    }
                },
                // Search and the map are tabs (M4-M8): Home's entry and pill switch to them.
                onSearch = tabs::openSearch,
                onMap = { tabs.select(AppTab.Map) },
                // Home slides aside for the pages it opens (M4-M6), all other features' since M4-M1c.
                opensFromHome = { it.isOpenedFromHome() },
                reducedMotion = reducedMotion,
                scrollToTop = scrollRequests,
            )

            // The Map tab's root: no Back; its "List" is Home.
            tripMapScreen(
                onBack = null,
                onList = { tabs.select(AppTab.Home) },
                onTripClick = navController::navigateToTripDetail,
                reducedMotion = reducedMotion,
            )

            // The Search tab's root: no Back, and the field takes the focus when Home's entry asks.
            searchTripsScreen(
                onBack = null,
                onTripClick = navController::navigateToTripDetail,
                isHome = { it.isHome() },
                reducedMotion = reducedMotion,
                focusOnEntry = false,
                focusRequests = searchFocusRequests,
                scrollToTop = scrollRequests,
            )

            settingsScreen(appVersion = appVersion, scrollToTop = scrollRequests)

            categoryTripsScreen(
                onBack = { navController.popBackStack() },
                onTripClick = navController::navigateToTripDetail,
                isHome = { it.isHome() },
                reducedMotion = reducedMotion,
            )

            sectionTripsScreen(
                onBack = { navController.popBackStack() },
                onTripClick = navController::navigateToTripDetail,
                isHome = { it.isHome() },
                reducedMotion = reducedMotion,
            )

            tripDetailScreen(
                onBack = { navController.popBackStack() },
                onContinue = navController::navigateToBooking,
                // The host card opens the host's page (M4-M10), in this tab's stack, bar hidden.
                onHostClick = navController::navigateToHostProfile,
                reducedMotion = reducedMotion,
            )

            hostProfileScreen(
                onBack = { navController.popBackStack() },
                onTripClick = navController::navigateToTripDetail,
                reducedMotion = reducedMotion,
            )

            bookingScreen(
                onBack = { navController.popBackStack() },
                onHeld = navController::navigateToHold,
            )

            // Released or run out: back to date + party under it, which re-reads its dates.
            holdScreen(onEnded = navController::returnToDateSelection)

            // Done: back to the root of the tab the booking was made in.
            bookingConfirmedScreen(onDone = { navController.popToRoot(tab) })
        }
    }
}

/** Home starts on splash until splash has run once in this process. */
internal fun AppTab.startRoute(splashDone: Boolean): Any = if (this == AppTab.Home && !splashDone) SplashRoute else rootRoute

/**
 * The route at the bottom of [this] tab's stack once splash is over. Internal, with [isRoot],
 * [isAt] and [popToRoot], for AppNavHostRootsTest (M4-M8 review #6).
 */
internal val AppTab.rootRoute: Any
    get() =
        when (this) {
            AppTab.Home -> TripListRoute
            AppTab.Search -> SearchTripsRoute
            AppTab.Map -> TripMapRoute
            AppTab.Settings -> SettingsRoute
        }

/** Whether [destination] is [this] tab's root: the bar shows there only. */
internal fun AppTab.isRoot(destination: NavDestination): Boolean =
    when (this) {
        AppTab.Home -> destination.hasRoute<TripListRoute>()
        AppTab.Search -> destination.hasRoute<SearchTripsRoute>()
        AppTab.Map -> destination.hasRoute<TripMapRoute>()
        AppTab.Settings -> destination.hasRoute<SettingsRoute>()
    }

internal fun NavController.isAt(tab: AppTab): Boolean = currentBackStackEntry?.destination?.let { tab.isRoot(it) } == true

/** Back to [tab]'s root; if it is not on the stack (it always is after splash), start the tab over at it. */
internal fun NavController.popToRoot(tab: AppTab) {
    if (!popBackStack(tab.rootRoute, inclusive = false) && !isAt(tab)) {
        navigate(tab.rootRoute) { popUpTo(graph.id) { inclusive = true } }
    }
}

private class NavControllerDeepLinkDestinations(
    private val navController: NavHostController,
    private val languages: AppLanguageState,
) : DeepLinkDestinations {
    override val language: AppLanguage get() = languages.language.value

    override val bookingInProgress: Boolean get() = navController.hasBookingInProgress()

    override fun switchLanguage(language: AppLanguage) = languages.switchTo(language)

    override fun openTrip(slug: String) {
        val placement = tripLinkPlacement(bookingInProgress = navController.hasBookingInProgress())
        navController.navigateToTripDetail(slug) {
            // Otherwise whatever was open goes and Home stays: back from the linked trip is Home.
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
