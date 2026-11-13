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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import eg.bahr.core.catalog.presentation.media.MediaViewerHost
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrSharedTransitions
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.currentAppLanguage
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
import eg.bahr.feature.booking.navigation.navigateToBookingByRef
import eg.bahr.feature.booking.navigation.navigateToBookingDetails
import eg.bahr.feature.booking.navigation.navigateToHold
import eg.bahr.feature.booking.navigation.navigateToHoldByRef
import eg.bahr.feature.booking.navigation.navigateToPaidBooking
import eg.bahr.feature.booking.navigation.openHeldSeats
import eg.bahr.feature.booking.navigation.openHoldScreen
import eg.bahr.feature.booking.navigation.returnToDateSelection
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.navigation.TripListRoute
import eg.bahr.feature.home.navigation.navigateToTripList
import eg.bahr.feature.home.navigation.tripListScreen
import eg.bahr.feature.host.navigation.HostsTabRoute
import eg.bahr.feature.host.navigation.hostProfileScreen
import eg.bahr.feature.host.navigation.hostsScreen
import eg.bahr.feature.host.navigation.navigateToHostProfile
import eg.bahr.feature.host.navigation.navigateToHosts
import eg.bahr.feature.map.navigation.TripMapRoute
import eg.bahr.feature.map.navigation.tripMapScreen
import eg.bahr.feature.mytrips.navigation.MyTripsRoute
import eg.bahr.feature.mytrips.navigation.myTripsScreen
import eg.bahr.feature.notifications.navigation.PushPromptReason
import eg.bahr.feature.notifications.navigation.PushPromptSlot
import eg.bahr.feature.notifications.navigation.UnreadAlerts
import eg.bahr.feature.notifications.navigation.navigateToNotifications
import eg.bahr.feature.notifications.navigation.notificationsScreen
import eg.bahr.feature.reviews.navigation.navigateToReviews
import eg.bahr.feature.reviews.navigation.navigateToWriteReview
import eg.bahr.feature.reviews.navigation.reviewsScreen
import eg.bahr.feature.reviews.navigation.writeReviewScreen
import eg.bahr.feature.search.navigation.navigateToSearchTrips
import eg.bahr.feature.search.navigation.searchTripsScreen
import eg.bahr.feature.settings.navigation.ProfileRoute
import eg.bahr.feature.settings.navigation.editPersonalInfoScreen
import eg.bahr.feature.settings.navigation.navigateToEditPersonalInfo
import eg.bahr.feature.settings.navigation.profileScreen
import eg.bahr.feature.splash.navigation.SplashRoute
import eg.bahr.feature.splash.navigation.splashScreen
import eg.bahr.feature.tripdetails.navigation.TripDetailRoute
import eg.bahr.feature.tripdetails.navigation.navigateToTripDetail
import eg.bahr.feature.tripdetails.navigation.tripDetailScreen
import eg.bahr.feature.triplisting.navigation.categoryTripsScreen
import eg.bahr.feature.triplisting.navigation.navigateToCategoryTrips
import eg.bahr.feature.triplisting.navigation.navigateToSectionTrips
import eg.bahr.feature.triplisting.navigation.sectionTripsScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
 * (after splash, once per process), the map, the hosts directory (MV-M1), My trips, Profile (M3-M0b; Alerts left
 * the bar in M3-M6a). Android composes the selected tab's NavHost under its Compose bar ([AppTabs]); iOS hosts
 * one per tab, each in its own Compose root under SwiftUI's `TabView`, fed by `IosTabs`. Search is no
 * tab since M3-M0b: it is pushed in the tab that opened it (Home's field, the Map header), bar hidden, and reopens on the last search.
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

    // The bell's count (M3-M6): one per app, which the notifications page keeps up with each change.
    val unreadAlerts: UnreadAlerts = koinInject()

    // Splash runs once per process; a NavHost rebuilt later (a language switch) starts on Home itself.
    val start: Any = remember(tab) { tab.startRoute(splashDone = tabs.splashDone) }

    val entry by navController.currentBackStackEntryAsState()
    val languages: AppLanguageState = koinInject()
    // The language this NavHost is built in: ProvideAppLanguage keys its content on it, so a switch rebuilds the NavHost.
    val builtIn = currentAppLanguage
    // The bar shows on this tab's root only. No dialog destinations since M3-M0b (sheets instead of dialogs): the bar
    // follows the top entry. This NavHost is the only writer of its tab's flag, and it reports every change of its back
    // stack, in order (MV-M8: a flag written from outside as well, and reported here only when it changed, could be left
    // saying "root" under a trip page).
    LaunchedEffect(navController, tabs) { tabs.followBackStack(tab, navController.atRootOf(tab), builtIn) }

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
        val pendingLink by inbox.pending.collectAsStateWithLifecycle()
        val pastSplash = entry?.destination?.let { !it.hasRoute<SplashRoute>() } ?: false
        val destinations = remember(navController, languages) { NavControllerDeepLinkDestinations(navController, languages) }
        LaunchedEffect(pendingLink, pastSplash) {
            if (pastSplash) inbox.dispatch(parser, destinations)
        }
        // A tapped hold reminder (M3-M7): that hold's seats, on top of what is open (or back to them if they are).
        val pendingHold by inbox.pendingHold.collectAsStateWithLifecycle()
        LaunchedEffect(pendingHold, pastSplash) {
            if (pastSplash) inbox.takeHold()?.let(navController::navigateToHoldByRef)
        }
    }

    // Shared elements (M4-M6): a Home chip or row title morphs into the page it opens.
    BahrSharedTransitions(backSwipe = backSwipe) {
        // The full-screen photo and video viewer (MV-M3) floats over every screen of this stack, inside the shared
        // transitions so a photo can fly between its thumbnail and the viewer. While it is open this tab shows no bar
        // (and, on iOS, no status bar or home indicator): the tab's own state, not one flag for every tab (MV-M8).
        MediaViewerHost(onOpenChange = { open -> tabs.reportViewerOpen(tab, open) }) {
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
                            // A "leaving soon" card names its date (MH-M1): the trip page opens on it if still listed.
                            is HomeAction.OpenTrip -> navController.navigateToTripDetail(TripDetailRoute(action.slug, action.date))
                            // A category chip, a `category` banner, or a category row's "See all" (M4-M1b).
                            is HomeAction.OpenCategory -> navController.navigateToCategoryTrips(action.key, action.title, action.sharedKey)
                            // Any other row's "See all": that row's whole list.
                            is HomeAction.OpenSection ->
                                navController.navigateToSectionTrips(
                                    action.sectionId,
                                    action.title,
                                    action.sharedKey,
                                )
                            // A host card (MH-M1): the host's public page, its header drawn from the card at once.
                            is HomeAction.OpenHost -> navController.navigateToHostProfile(action.slug, action.name, action.avatar)
                            // A hosts row's "See all" (MV-M1): the hosts directory in Home's stack, top rated first.
                            HomeAction.OpenHosts -> navController.navigateToHosts()
                        }
                    },
                    // Search opens full screen in this tab (M3-M0b); the map is a tab (M4-M8), which the pill switches to.
                    onSearch = { navController.navigateToSearchTrips() },
                    onMap = { tabs.select(AppTab.Map) },
                    // The bell (M3-M6a; the Alerts tab before) pushes the notifications page over Home, bar
                    // hidden; Back comes back to Home. Its badge is the inbox's unread count (M3-M6).
                    onAlerts = navController::navigateToNotifications,
                    unreadAlerts = unreadAlerts.count,
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
                    // MV-M1: a matching host opens their page; "See all hosts" the directory with the same text.
                    onHostClick = { host -> navController.navigateToHostProfile(host.slug, host.name, host.avatar) },
                    onSeeAllHosts = { q -> navController.navigateToHosts(q = q) },
                )

                // The hosts directory (MV-M1): the Hosts tab's root, and pushed from Home's hosts "See all" and
                // search's "See all hosts". A host opens their page on top, so Back returns to the list with its filters.
                hostsScreen(
                    onBack = { navController.popBackStack() },
                    onHostClick = { slug, name, avatar -> navController.navigateToHostProfile(slug, name, avatar) },
                    scrollToTop = scrollRequests,
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

                // The notifications page (M3-M6; pushed from Home's bell since M3-M6a): a tap opens the item's page on top,
                // so Back returns to the list. My reviews (M3-M4) is not built yet: a review's item only reads.
                notificationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTrip = {
                        slug,
                        date,
                        placeOpened,
                        ->
                        navController.navigateToTripDetail(TripDetailRoute(slug, date, placeOpened))
                    },
                    onOpenBooking = navController::navigateToBookingByRef,
                )

                // The My trips tab's root (M3-M0): signed out it offers the sign-in sheet; a booking row opens the booking's
                // details (MV-M6, feature:booking) on top, a waiting-list row its trip, a finished booking's "Rate your trip"
                // the review page (M3-M4, feature:reviews), all in this tab's stack, bar hidden.
                myTripsScreen(
                    onSignIn = navController::navigateToSignIn,
                    onTripClick = navController::navigateToTripDetail,
                    onBookingClick = navController::navigateToBookingDetails,
                    onRateTrip = { ref, title -> navController.navigateToWriteReview(ref, title) },
                    scrollToTop = scrollRequests,
                )

                // The review page (M3-M4): back returns to the page under it. "Done" (sent) returns to My trips, which
                // reads its bookings again and shows the review's status: also from a booking's details (MV-M6), whose
                // "Rate your trip" would otherwise still show (`getBooking` does not say a booking was reviewed).
                writeReviewScreen(
                    onBack = { navController.popBackStack() },
                    onDone = navController::popToMyTripsOrBack,
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
                    // Right after a waiting-list join (M3-M5): feature:notifications' push pre-prompt.
                    pushPrompt = { onDone -> PushPromptSlot(onDone) },
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

                // Released or run out: back to date + party under it, which re-reads its dates. A hold just placed offers
                // feature:notifications' pre-prompt (M3-M7): the "about to expire" reminder needs the permission. Paying
                // (M3-M1) asks feature:auth's sign-in sheet over it when signed out, and hands over to the booking once paid.
                holdScreen(
                    onEnded = navController::returnToDateSelection,
                    onPaid = navController::navigateToPaidBooking,
                    onSignIn = { navController.navigateToSignIn() },
                    reminderPrompt = { onDone -> PushPromptSlot(onDone = onDone, reason = PushPromptReason.HoldReminder) },
                )

                // A tapped hold reminder (M3-M7): the hold read again, then its seats in its place; over → back, where the
                // app says the seats were released.
                openHoldScreen(
                    onOpen = navController::openHeldSeats,
                    onEnded = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )

                // The booking's ticket. After paying, "Trips" goes back to the root of the tab the booking was made in.
                // As a booking's details (MV-M6: a My trips row, a notification) it opens the trip's page and, when My
                // trips says the booking may be rated, the review page, on top; Back returns to where it was opened.
                bookingConfirmedScreen(
                    onDone = { navController.popToRoot(tab) },
                    onBack = { navController.popBackStack() },
                    onViewTrip = navController::navigateToTripDetail,
                    onRateTrip = { ref, title -> navController.navigateToWriteReview(ref, title) },
                )
            }
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
            AppTab.Hosts -> HostsTabRoute
            AppTab.MyTrips -> MyTripsRoute
            AppTab.Profile -> ProfileRoute
        }

/** Whether [destination] is [this] tab's root: the bar shows there only. */
internal fun AppTab.isRoot(destination: NavDestination): Boolean =
    when (this) {
        AppTab.Home -> destination.hasRoute<TripListRoute>()
        AppTab.Map -> destination.hasRoute<TripMapRoute>()
        AppTab.Hosts -> destination.hasRoute<HostsTabRoute>()
        AppTab.MyTrips -> destination.hasRoute<MyTripsRoute>()
        AppTab.Profile -> destination.hasRoute<ProfileRoute>()
    }

/** Whether [tab]'s root is on top of this NavController's stack, at each change of the top entry (MV-M8). */
internal fun NavController.atRootOf(tab: AppTab): Flow<Boolean> = currentBackStackEntryFlow.map { tab.isRoot(it.destination) }

internal fun NavController.isAt(tab: AppTab): Boolean = currentBackStackEntry?.destination?.let { tab.isRoot(it) } == true

/** Back to [tab]'s root; if it is not on the stack (it always is after splash), start the tab over at it. */
internal fun NavController.popToRoot(tab: AppTab) {
    if (!popBackStack(tab.rootRoute, inclusive = false) && !isAt(tab)) {
        navigate(tab.rootRoute) { popUpTo(graph.id) { inclusive = true } }
    }
}

/**
 * A sent review's "Done" (M3-M4, MV-M6): back to My trips when it is under the review page (it always is today: "Rate
 * your trip" is on My trips and on a booking's details opened from it), popping the details too; else one step back.
 * Internal for AppNavHostRootsTest.
 */
internal fun NavController.popToMyTripsOrBack() {
    if (!popBackStack(MyTripsRoute, inclusive = false)) popBackStack()
}

/**
 * The same trip link already on top (iOS may hand one over twice): it is not pushed again. Compared by the route's
 * arguments, not single-top: a push for another date of the trip on top must open on that date (M3-M5). Internal for
 * AppNavHostRootsTest (M3-M5 review #5).
 */
internal fun NavController.isShowing(route: TripDetailRoute): Boolean {
    val top = currentBackStackEntry ?: return false
    return top.destination.hasRoute<TripDetailRoute>() && top.toRoute<TripDetailRoute>() == route
}

/** How a link or a tapped push opens in Home's stack. Internal for TabBarPathsTest (MV-M8). */
internal class NavControllerDeepLinkDestinations(
    private val navController: NavHostController,
    private val languages: AppLanguageState,
) : DeepLinkDestinations {
    override val language: AppLanguage get() = languages.language.value

    override val bookingInProgress: Boolean get() = navController.hasBookingInProgress()

    override fun switchLanguage(language: AppLanguage) = languages.switchTo(language)

    override fun openTrip(
        slug: String,
        date: String?,
        placeOpened: Boolean,
    ) {
        val route = TripDetailRoute(slug, date, placeOpened)
        if (navController.isShowing(route)) return
        val placement = tripLinkPlacement(bookingInProgress = navController.hasBookingInProgress())
        navController.navigateToTripDetail(route) {
            // Otherwise whatever was open goes and Home stays: back from the linked trip is Home.
            if (placement == TripLinkPlacement.AboveList) popUpTo<TripListRoute>()
        }
    }

    override fun openList() {
        if (!navController.popBackStack<TripListRoute>(inclusive = false)) {
            navController.navigateToTripList { launchSingleTop = true }
        }
    }
}
