package eg.bahr.navigation

import android.app.Application
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.presentation.media.LocalMediaViewer
import eg.bahr.core.catalog.presentation.media.MediaViewerController
import eg.bahr.core.catalog.presentation.media.MediaViewerHost
import eg.bahr.core.catalog.presentation.media.ViewerMedia
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.di.AppLanguageState
import eg.bahr.feature.booking.navigation.BookingConfirmedRoute
import eg.bahr.feature.booking.navigation.navigateToBookingByRef
import eg.bahr.feature.booking.navigation.navigateToBookingDetails
import eg.bahr.feature.home.navigation.TripListRoute
import eg.bahr.feature.host.navigation.HostProfileRoute
import eg.bahr.feature.host.navigation.HostsRoute
import eg.bahr.feature.host.navigation.HostsTabRoute
import eg.bahr.feature.host.navigation.navigateToHostProfile
import eg.bahr.feature.host.navigation.navigateToHosts
import eg.bahr.feature.map.navigation.TripMapRoute
import eg.bahr.feature.mytrips.navigation.MyTripsRoute
import eg.bahr.feature.notifications.navigation.NotificationsRoute
import eg.bahr.feature.notifications.navigation.navigateToNotifications
import eg.bahr.feature.reviews.navigation.ReviewsRoute
import eg.bahr.feature.search.navigation.SearchTripsRoute
import eg.bahr.feature.search.navigation.navigateToSearchTrips
import eg.bahr.feature.settings.navigation.ProfileRoute
import eg.bahr.feature.splash.navigation.SplashRoute
import eg.bahr.feature.tripdetails.navigation.TripDetailRoute
import eg.bahr.feature.tripdetails.navigation.navigateToTripDetail
import eg.bahr.feature.triplisting.navigation.CategoryTripsRoute
import eg.bahr.feature.triplisting.navigation.SectionTripsRoute
import eg.bahr.feature.triplisting.navigation.navigateToCategoryTrips
import eg.bahr.feature.triplisting.navigation.navigateToSectionTrips
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MV-M8 (the bar over the trip page on iOS): the bar's visibility as [AppTabsState] derives it, along every path to a
 * trip page, on the features' real routes and `navigateToX` calls. Each tab is reported the way [AppNavHost] reports
 * it: [AppTabsState.followBackStack] over [atRootOf], and the real [MediaViewerHost]'s `onOpenChange`. The
 * destinations are empty stand-ins (the screens need the network and Koin); the trip page's stand-in keeps the viewer
 * controller so a test can open and close the viewer over it.
 *
 * What iOS then shows is the selected tab's [AppTabsState.Snapshot.barVisible] (IosBarMirror, TabBarModel.isBarHidden).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TabBarPathsTest {
    @get:Rule
    val compose = createComposeRule()

    private val tabs = AppTabsState(splashDone = true)
    private lateinit var nav: TestNavHostController
    private var viewer: MediaViewerController? = null
    private val languageScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = languageScope.cancel()

    @Test
    fun `a Home card opens the trip page - bar hidden - Back shows it`() {
        show(AppTab.Home)
        assertBar(visible = true, "Home's root")

        onUi { nav.navigateToTripDetail(SLUG) }
        assertBar(visible = false, "trip page")

        onUi { nav.popBackStack() }
        assertBar(visible = true, "back on Home")
    }

    @Test
    fun `See all and a category open a list then the trip - bar hidden on both`() {
        show(AppTab.Home)

        onUi { nav.navigateToSectionTrips(SECTION) }
        assertBar(visible = false, "a row's See all")
        onUi { nav.navigateToTripDetail(SLUG) }
        assertBar(visible = false, "trip from See all")
        onUi { nav.popBackStack(TripListRoute, inclusive = false) }
        assertBar(visible = true)

        onUi { nav.navigateToCategoryTrips(CATEGORY) }
        assertBar(visible = false, "a category")
        onUi { nav.navigateToTripDetail(SLUG) }
        assertBar(visible = false, "trip from a category")
    }

    @Test
    fun `search over Home and over the map opens the trip - bar hidden`() {
        listOf(AppTab.Home, AppTab.Map).forEach { tab ->
            show(tab)
            onUi { nav.navigateToSearchTrips() }
            assertBar(visible = false, "${tab.name}: search")
            onUi { nav.navigateToTripDetail(SLUG) }
            assertBar(visible = false, "${tab.name}: trip from search")
            onUi {
                nav.popBackStack()
                nav.popBackStack()
            }
            assertBar(visible = true, "${tab.name}: root")
        }
    }

    @Test
    fun `the map card opens the trip in the Map tab - bar hidden`() {
        show(AppTab.Map)

        onUi { nav.navigateToTripDetail(SLUG) }
        assertBar(visible = false)
    }

    @Test
    fun `Hosts tab - host - trip keeps the bar hidden - and Back Back shows it`() {
        show(AppTab.Hosts)

        onUi { nav.navigateToHostProfile(SLUG) }
        assertBar(visible = false, "host page")
        onUi { nav.navigateToTripDetail(SLUG) }
        assertBar(visible = false, "trip from the host")
        onUi { nav.popBackStack() }
        assertBar(visible = false, "back on the host page")
        onUi { nav.popBackStack() }
        assertBar(visible = true, "the directory")
    }

    @Test
    fun `Home's hosts See all - host - trip keeps the bar hidden`() {
        show(AppTab.Home)

        onUi {
            nav.navigateToHosts()
            nav.navigateToHostProfile(SLUG)
            nav.navigateToTripDetail(SLUG)
        }
        assertBar(visible = false)
    }

    @Test
    fun `My trips - booking details - View trip keeps the bar hidden`() {
        show(AppTab.MyTrips)

        onUi { nav.navigateToBookingDetails(REF) }
        assertBar(visible = false, "details")
        onUi { nav.navigateToTripDetail(SLUG) }
        assertBar(visible = false, "trip from the details")
        onUi {
            nav.popBackStack()
            nav.popBackStack()
        }
        assertBar(visible = true, "My trips")
    }

    @Test
    fun `a link or a tapped push opens the trip over Home - bar hidden - from any tab`() {
        // A link while another tab is selected: the app switches to Home, whose NavHost opens it.
        show(AppTab.Profile)
        show(AppTab.Home)
        tabs.select(AppTab.Profile)
        assertBar(visible = true, "Profile's root")

        tabs.select(AppTab.Home)
        onUi { destinations().openTrip(SLUG, date = null, placeOpened = false) }
        assertBar(visible = false, "linked trip")

        // A push's place-opened link, or a notification's booking then its trip.
        onUi { destinations().openTrip(SLUG, date = DATE, placeOpened = true) }
        assertBar(visible = false, "pushed trip")
        onUi {
            nav.navigateToNotifications()
            nav.navigateToBookingByRef(REF)
            nav.navigateToTripDetail(SLUG)
        }
        assertBar(visible = false, "trip from a notification's booking")
    }

    /** MV-M3's viewer over the trip page: open hides the bar (it already is), close leaves it hidden; Back shows it. */
    @Test
    fun `the viewer opened and closed on a trip page leaves the bar hidden`() {
        show(AppTab.Home)
        onUi { nav.navigateToTripDetail(SLUG) }

        onUi { viewer!!.open(listOf(ViewerMedia.Photo(PHOTO)), index = 0) }
        assertBar(visible = false, "viewer open")
        assertTrue(tabs.state.value.fullScreen, "status bar hidden too")

        onUi { viewer!!.close() }
        compose.mainClock.advanceTimeBy(VIEWER_FADE_MS)
        compose.waitForIdle()
        assertFalse(tabs.state.value.fullScreen, "viewer closed")
        assertBar(visible = false, "still on the trip page")

        onUi { nav.popBackStack() }
        assertBar(visible = true, "back on Home")
    }

    /** A host's page and "See all reviews" open the viewer too (the avatar, review photos): the same rule. */
    @Test
    fun `the viewer over a host page or the reviews closes to a hidden bar`() {
        show(AppTab.Hosts)
        onUi {
            nav.navigateToHostProfile(SLUG)
            nav.navigate(ReviewsRoute(host = SLUG))
        }

        onUi { viewer!!.open(listOf(ViewerMedia.Photo(PHOTO)), index = 0) }
        onUi { viewer!!.close() }
        compose.mainClock.advanceTimeBy(VIEWER_FADE_MS)
        compose.waitForIdle()

        assertBar(visible = false)
    }

    /** A re-tap off the root pops to it (AppNavHost's reselect): the bar comes back. */
    @Test
    fun `a re-tap on a trip page pops to the root - bar shown`() {
        show(AppTab.Map)
        onUi { nav.navigateToTripDetail(SLUG) }
        assertBar(visible = false)

        onUi { nav.popBackStack(AppTab.Map.rootRoute, inclusive = false) }

        assertBar(visible = true)
    }

    /**
     * The user's bug: a `?lang=` link switches the language, which rebuilds Home's NavHost, and the link's trip opens in
     * the new one. However late the tab state hears of the language, the bar stays hidden over the trip.
     */
    @Test
    fun `a language switch with a trip opened in the new language keeps the bar hidden - whichever lands first`() {
        listOf(true, false).forEach { languageFirst ->
            val state = AppTabsState(splashDone = true).apply { onLanguage(AppLanguage.ARABIC) }
            show(AppTab.Home, language = AppLanguage.ARABIC, state = state)
            onUi { nav.navigateToTripDetail(SLUG) }

            if (languageFirst) state.onLanguage(AppLanguage.ENGLISH)
            show(AppTab.Home, language = AppLanguage.ENGLISH, state = state)
            onUi { nav.navigateToTripDetail(SLUG) }
            if (!languageFirst) state.onLanguage(AppLanguage.ENGLISH)

            assertFalse(state.state.value.barVisible, "language first: $languageFirst")
        }
    }

    // ---------- Plumbing ----------

    private fun assertBar(
        visible: Boolean,
        message: String? = null,
    ) {
        compose.waitForIdle()
        if (visible) assertTrue(tabs.state.value.barVisible, message) else assertFalse(tabs.state.value.barVisible, message)
    }

    /** A fresh NavHost for [tab], at its root, selected, reporting to [state] as AppNavHost does; one per call. */
    private fun show(
        tab: AppTab,
        language: AppLanguage? = null,
        state: AppTabsState = tabs,
    ) {
        state.select(tab)
        val fresh = TestNavHostController(RuntimeEnvironment.getApplication())
        fresh.navigatorProvider.addNavigator(ComposeNavigator())
        nav = fresh
        val shown = Shown(fresh, tab, language, state)
        val current = host
        if (current == null) {
            host = mutableStateOf(shown)
            compose.setContent {
                val (controller, shownTab, builtIn, reportTo) = host!!.value
                key(controller) {
                    LaunchedEffect(controller, reportTo) { reportTo.followBackStack(shownTab, controller.atRootOf(shownTab), builtIn) }
                    ProvideAppLanguage(AppLanguage.ENGLISH) {
                        BahrTheme(locale = BahrLocale.English) {
                            MediaViewerHost(onOpenChange = { open -> reportTo.reportViewerOpen(shownTab, open) }) {
                                NavHost(navController = controller, startDestination = shownTab.rootRoute) {
                                    composable<SplashRoute> {}
                                    composable<TripListRoute> {}
                                    composable<SearchTripsRoute> {}
                                    composable<TripMapRoute> {}
                                    composable<HostsTabRoute> {}
                                    composable<HostsRoute> {}
                                    composable<HostProfileRoute> {}
                                    composable<MyTripsRoute> {}
                                    composable<NotificationsRoute> {}
                                    composable<ProfileRoute> {}
                                    composable<CategoryTripsRoute> {}
                                    composable<SectionTripsRoute> {}
                                    composable<BookingConfirmedRoute> {}
                                    composable<ReviewsRoute> { viewer = LocalMediaViewer.current }
                                    composable<TripDetailRoute> { viewer = LocalMediaViewer.current }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            compose.runOnUiThread { current.value = shown }
        }
        compose.waitForIdle()
    }

    private data class Shown(
        val controller: TestNavHostController,
        val tab: AppTab,
        val language: AppLanguage?,
        val state: AppTabsState,
    )

    private var host: MutableState<Shown>? = null

    /** The deep-link destinations AppNavHost gives the inbox, over the current controller. */
    private fun destinations() =
        NavControllerDeepLinkDestinations(
            nav,
            AppLanguageState(stored = MutableStateFlow(AppLanguage.ARABIC), store = {}, scope = languageScope),
        )

    private fun onUi(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    private companion object {
        const val SLUG = "lake-burullus-birds"
        const val SECTION = "leaving-soon"
        const val CATEGORY = "birds"
        const val REF = "BRL-7K4M2X9P"
        const val DATE = "2026-11-20"

        /** Longer than the viewer's fade out (BahrMotion.Medium). */
        const val VIEWER_FADE_MS = 1_000L
        val PHOTO = ImageDto(url = "http://127.0.0.1:9/photo.png", width = 900, height = 560, alt = "Photo")
    }
}
