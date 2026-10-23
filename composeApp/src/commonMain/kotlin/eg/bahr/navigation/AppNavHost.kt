package eg.bahr.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
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
import eg.bahr.feature.auth.navigation.DeleteAccountSheetSlot
import eg.bahr.feature.auth.navigation.SignInButtonsSlot
import eg.bahr.feature.auth.navigation.navigateToSignIn
import eg.bahr.feature.auth.navigation.signInScreen
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
import eg.bahr.feature.home.navigation.navigateToTripList
import eg.bahr.feature.home.navigation.tripListScreen
import eg.bahr.feature.host.navigation.hostProfileScreen
import eg.bahr.feature.host.navigation.navigateToHostProfile
import eg.bahr.feature.map.navigation.TripMapRoute
import eg.bahr.feature.map.navigation.tripMapScreen
import eg.bahr.feature.mytrips.navigation.MyTripsRoute
import eg.bahr.feature.mytrips.navigation.myTripsScreen
import eg.bahr.feature.notifications.navigation.NotificationsRoute
import eg.bahr.feature.notifications.navigation.notificationsScreen
import eg.bahr.feature.reviews.navigation.navigateToReviews
import eg.bahr.feature.reviews.navigation.reviewsScreen
import eg.bahr.feature.search.navigation.navigateToSearchTrips
import eg.bahr.feature.search.navigation.searchTripsScreen
import eg.bahr.feature.settings.navigation.ProfileRoute
import eg.bahr.feature.settings.navigation.editPersonalInfoScreen
import eg.bahr.feature.settings.navigation.navigateToEditPersonalInfo
import eg.bahr.feature.settings.navigation.profileScreen
import eg.bahr.feature.splash.navigation.SplashRoute
import eg.bahr.feature.splash.navigation.splashScreen
import eg.bahr.feature.tripdetails.navigation.navigateToTripDetail
import eg.bahr.feature.tripdetails.navigation.tripDetailScreen
import eg.bahr.feature.triplisting.navigation.categoryTripsScreen
import eg.bahr.feature.triplisting.navigation.navigateToCategoryTrips
import eg.bahr.feature.triplisting.navigation.navigateToSectionTrips
import eg.bahr.feature.triplisting.navigation.sectionTripsScreen
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
 * opened from the map stays in the Map tab while Home keeps its scroll. Only the start differs: Home
 * (after splash, once per process), the map, My trips, Alerts, Profile (M3-M0b). Android composes the
 * selected tab's NavHost under its Compose bar ([AppTabs]); iOS hosts one per tab, each in its own
 * Compose root under SwiftUI's `TabView`, fed by `IosTabs`. Search is no tab since M3-M0b: it is pushed in
 * the tab that opened it (Home's field, the Map header), bar hidden, and reopens on the last search.
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

    // Whether a back swipe is driving the pop under way, and from which edge (M3-M12): the destinations'
    // dim asks (navigation 2.10 hands the NavHost's swipe transitions the edge itself, AppScreenTransitions).
    // Read from the dispatcher's state when a transition starts, never in composition.
    val navigationEvents = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val backSwipe = remember(navigationEvents) { { navigationEvents.backSwipe() } }
    val transitions = remember(backSwipe) { AppScreenTransitions(reducedMotion, backSwipe) }

    // Splash runs once per process; a NavHost rebuilt later (a language switch) starts on Home itself.
    val start: Any = remember(tab) { tab.startRoute(splashDone = tabs.splashDone) }

    // The bar shows on this tab's root only.
    val entry by navController.currentBackStackEntryAsState()
    // No dialog destinations since M3-M0b (sheets instead of dialogs): the bar follows the top entry.
    val atRoot = entry?.destination?.let { tab.isRoot(it) }
    // Synchronous, so a keyed SideEffect (Compose 1.12) rather than a coroutine (Compose audit L9).
    SideEffect(atRoot) { atRoot?.let { tabs.reportAtRoot(tab, it) } }

    // Re-taps of this tab reach the root's screen through a conflated channel, which holds a signal until
    // the screen collects it (a first visit).
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
    BahrSharedTransitions(backSwipe = backSwipe) {
        // One screen transition for every push and pop, in the platform's style (AppScreenTransitions).
        AppScreenNavHost(navController = navController, startDestination = start, transitions = transitions) {
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
                // Search opens full screen in this tab (M3-M0b); the map is a tab (M4-M8), which the pill switches to.
                onSearch = { navController.navigateToSearchTrips() },
                onMap = { tabs.select(AppTab.Map) },
                scrollToTop = scrollRequests,
            )

            // The Map tab's root: no Back; its "List" is Home, its search button the full-screen search.
            tripMapScreen(
                onBack = null,
                onList = { tabs.select(AppTab.Home) },
                onSearch = { navController.navigateToSearchTrips() },
                onTripClick = navController::navigateToTripDetail,
            )

            // Search (M3-M0b: no longer a tab), pushed from Home's field or the Map header; the field takes
            // the focus as it opens.
            searchTripsScreen(
                onBack = { navController.popBackStack() },
                onTripClick = navController::navigateToTripDetail,
            )

            // The Profile tab's root (M3-M0b; Settings before): feature:auth's sign-in buttons when signed
            // out, and its delete-account dialog.
            profileScreen(
                appVersion = appVersion,
                signInButtons = { modifier -> SignInButtonsSlot(modifier) },
                onEditPersonalInfo = navController::navigateToEditPersonalInfo,
                deleteAccountSheet = { onDismiss -> DeleteAccountSheetSlot(onDismiss) },
                scrollToTop = scrollRequests,
            )

            editPersonalInfoScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )

            // The Alerts tab's root (M3-M0b): the empty or signed-out state until M3-B9 / M3-M6.
            notificationsScreen(
                signInButtons = { modifier -> SignInButtonsSlot(modifier) },
                scrollToTop = scrollRequests,
            )

            // The My trips tab's root (M3-M0): signed out it offers the sign-in sheet; a row opens its trip.
            myTripsScreen(
                onSignIn = navController::navigateToSignIn,
                onTripClick = navController::navigateToTripDetail,
                scrollToTop = scrollRequests,
            )

            // The sign-in sheet, pushed in whichever tab asked (bar hidden); closing or finishing goes back
            // there, which reads the new session itself.
            signInScreen(
                onDone = { navController.popBackStack() },
                onClose = { navController.popBackStack() },
            )

            categoryTripsScreen(
                onBack = { navController.popBackStack() },
                onTripClick = navController::navigateToTripDetail,
            )

            sectionTripsScreen(
                onBack = { navController.popBackStack() },
                onTripClick = navController::navigateToTripDetail,
            )

            tripDetailScreen(
                onBack = { navController.popBackStack() },
                onContinue = navController::navigateToBooking,
                // The host card opens the host's page (M4-M10), in this tab's stack, bar hidden.
                onHostClick = { host -> navController.navigateToHostProfile(host.slug, host.name, host.avatar) },
                // "See all reviews" (M4-M11), in this tab's stack, bar hidden.
                onSeeAllReviews = { slug, title -> navController.navigateToReviews(trip = slug, title = title) },
            )

            hostProfileScreen(
                onBack = { navController.popBackStack() },
                onTripClick = navController::navigateToTripDetail,
                onSeeAllReviews = { host, name -> navController.navigateToReviews(host = host, title = name) },
            )

            reviewsScreen(onBack = { navController.popBackStack() })

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
            AppTab.Map -> TripMapRoute
            AppTab.MyTrips -> MyTripsRoute
            AppTab.Alerts -> NotificationsRoute
            AppTab.Profile -> ProfileRoute
        }

/** Whether [destination] is [this] tab's root: the bar shows there only. */
internal fun AppTab.isRoot(destination: NavDestination): Boolean =
    when (this) {
        AppTab.Home -> destination.hasRoute<TripListRoute>()
        AppTab.Map -> destination.hasRoute<TripMapRoute>()
        AppTab.MyTrips -> destination.hasRoute<MyTripsRoute>()
        AppTab.Alerts -> destination.hasRoute<NotificationsRoute>()
        AppTab.Profile -> destination.hasRoute<ProfileRoute>()
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
