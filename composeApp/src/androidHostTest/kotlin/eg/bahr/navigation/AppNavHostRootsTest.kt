package eg.bahr.navigation

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import eg.bahr.feature.booking.navigation.BookingConfirmedRoute
import eg.bahr.feature.booking.navigation.BookingRoute
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.navigation.navigateToBooking
import eg.bahr.feature.booking.navigation.navigateToBookingConfirmed
import eg.bahr.feature.booking.navigation.navigateToHold
import eg.bahr.feature.home.navigation.TripListRoute
import eg.bahr.feature.map.navigation.TripMapRoute
import eg.bahr.feature.mytrips.navigation.MyTripsRoute
import eg.bahr.feature.notifications.navigation.NotificationsRoute
import eg.bahr.feature.search.navigation.SearchTripsRoute
import eg.bahr.feature.search.navigation.navigateToSearchTrips
import eg.bahr.feature.settings.navigation.EditPersonalInfoRoute
import eg.bahr.feature.settings.navigation.ProfileRoute
import eg.bahr.feature.settings.navigation.navigateToEditPersonalInfo
import eg.bahr.feature.splash.navigation.SplashRoute
import eg.bahr.feature.tripdetails.navigation.TripDetailRoute
import eg.bahr.feature.tripdetails.navigation.navigateToTripDetail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AppNavHost]'s real root mapping (M4-M8 review #6): [rootRoute], [isRoot], [isAt] and [popToRoot],
 * on a [TestNavHostController] over the features' real routes and their real `navigateToX` calls.
 * The destinations are empty stand-ins: the screens need the network and Koin, and their own tests
 * cover them. [AppTabsTest] covers what the host does with "at root" (the bar) and re-taps.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the app's own starts Koin, which these stand-in destinations do not need.
@Config(application = Application::class)
class AppNavHostRootsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: TestNavHostController

    @Test
    fun `each tab's root is its root - and no other tab's`() {
        AppTab.entries.forEach { tab ->
            show(start = tab.rootRoute)
            AppTab.entries.forEach { other ->
                assertEquals(other == tab, nav.isAt(other), "${tab.name}'s root seen from ${other.name}")
            }
        }
    }

    @Test
    fun `Home starts on splash once - which is not a root`() {
        assertEquals(SplashRoute, AppTab.Home.startRoute(splashDone = false))
        assertEquals(TripListRoute, AppTab.Home.startRoute(splashDone = true))
        AppTab.entries.filter { it != AppTab.Home }.forEach { assertEquals(it.rootRoute, it.startRoute(splashDone = false)) }

        show(start = SplashRoute)
        assertFalse(nav.isAt(AppTab.Home), "splash hides the bar")
    }

    @Test
    fun `a pushed trip page is not the root - Back to it is`() {
        show(start = TripMapRoute)

        onUi { nav.navigateToTripDetail(SLUG) }
        assertFalse(nav.isAt(AppTab.Map))

        onUi { nav.popBackStack() }
        assertTrue(nav.isAt(AppTab.Map))
    }

    /**
     * M3-M0b: search is no tab; Home's field and the Map header push it in their own tab, which hides
     * the bar (not a root), and Back comes back to the root with the bar. A trip opened from it keeps the
     * search under it.
     */
    @Test
    fun `search opens over Home and over the map - not a root - and Back returns`() {
        listOf(AppTab.Home, AppTab.Map).forEach { tab ->
            show(start = tab.rootRoute)

            onUi { nav.navigateToSearchTrips() }
            assertFalse(nav.isAt(tab), "${tab.name}: search hides the bar")
            onUi { nav.navigateToTripDetail(SLUG) }
            onUi { nav.popBackStack() }
            assertEquals(
                listOf(tab.rootRoute::class.simpleName, SearchTripsRoute::class.simpleName),
                screens(),
                "${tab.name}: search kept under the trip",
            )

            onUi { nav.popBackStack() }
            assertTrue(nav.isAt(tab), "${tab.name}: back on its root")
        }
    }

    @Test
    fun `Edit personal info is pushed over Profile - not a root`() {
        show(start = ProfileRoute)

        onUi { nav.navigateToEditPersonalInfo() }
        assertFalse(nav.isAt(AppTab.Profile))

        onUi { nav.popBackStack() }
        assertTrue(nav.isAt(AppTab.Profile))
    }

    @Test
    fun `the confirmation's Done goes back to the root of the tab the booking was made in`() {
        AppTab.entries.forEach { tab ->
            show(start = tab.rootRoute)
            onUi {
                nav.navigateToTripDetail(SLUG)
                nav.navigateToBooking(SLUG, DEPARTURE)
                nav.navigateToHold(hold)
                nav.navigateToBookingConfirmed(REF)
            }
            assertFalse(nav.isAt(tab))

            onUi { nav.popToRoot(tab) }

            assertTrue(nav.isAt(tab), "${tab.name}: back on its root")
            assertEquals(listOf(tab.rootRoute::class.simpleName), screens(), "${tab.name}: nothing left above the root")
        }
    }

    @Test
    fun `with the root not on the stack - Done starts the tab over at it`() {
        // A Home NavHost whose root was popped (a link opened with nothing under it).
        show(start = TripDetailRoute(SLUG))
        onUi { nav.navigateToBookingConfirmed(REF) }

        onUi { nav.popToRoot(AppTab.Home) }

        assertTrue(nav.isAt(AppTab.Home))
        assertEquals(listOf(TripListRoute::class.simpleName), screens())
    }

    // ---------- Plumbing ----------

    /** A fresh controller and NavHost started at [start]; a test may call it once per tab. */
    private fun show(start: Any) {
        val fresh = TestNavHostController(RuntimeEnvironment.getApplication())
        fresh.navigatorProvider.addNavigator(ComposeNavigator())
        nav = fresh
        if (host == null) {
            host = mutableStateOf(fresh to start)
            compose.setContent {
                val (controller, startRoute) = host!!.value
                // A new controller is a new NavHost: one compose rule takes one setContent.
                key(controller) {
                    NavHost(navController = controller, startDestination = startRoute) {
                        composable<SplashRoute> {}
                        composable<TripListRoute> {}
                        composable<SearchTripsRoute> {}
                        composable<TripMapRoute> {}
                        composable<MyTripsRoute> {}
                        composable<NotificationsRoute> {}
                        composable<ProfileRoute> {}
                        composable<EditPersonalInfoRoute> {}
                        composable<TripDetailRoute> {}
                        composable<BookingRoute> {}
                        composable<HoldRoute> {}
                        composable<BookingConfirmedRoute> {}
                    }
                }
            }
        } else {
            compose.runOnUiThread { host!!.value = fresh to start }
        }
        compose.waitForIdle()
    }

    private var host: MutableState<Pair<TestNavHostController, Any>>? = null

    private fun onUi(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    /** The screens on the back stack, bottom first, by route class (the graph's own entry left out). */
    private fun screens(): List<String?> =
        nav.currentBackStack.value
            .map { it.destination }
            .filter { it !is NavGraph }
            .map {
                it.route
                    ?.substringBefore('/')
                    ?.substringBefore('?')
                    ?.substringAfterLast('.')
            }

    private val hold =
        HoldRoute(
            ref = REF,
            holdExpiresAt = "2026-10-11T10:15:00Z",
            serverNow = "2026-10-11T10:00:00Z",
            totalAmount = 90000,
            totalCurrency = "EGP",
            guestPhone = "+201012345678",
        )

    private companion object {
        const val SLUG = "lake-burullus-birds"
        const val DEPARTURE = "dep-1"
        const val REF = "BRL-7K4M2X9P"
    }
}
