package eg.bahr.navigation

import eg.bahr.navigation.AppTabsState.ReselectAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M4-M8's tab rules, as the bar follows them: switching, re-taps, the bar on tab roots only. The
 * NavHosts and the bar only report to and read from this state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppTabsStateTest {
    @Test
    fun `the app starts on Home - with the bar hidden behind splash`() {
        val tabs = AppTabsState()

        assertEquals(AppTab.Home, tabs.selected)
        assertFalse(tabs.state.value.barVisible)

        tabs.reportAtRoot(AppTab.Home, true)
        assertTrue(tabs.state.value.barVisible)
    }

    @Test
    fun `a tap on another tab selects it - and is not a re-tap`() =
        runTest {
            val tabs = AppTabsState(splashDone = true)
            val reselected = mutableListOf<AppTab>()
            val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tabs.reselected.toList(reselected) }

            tabs.onTabTapped(AppTab.Map)

            assertEquals(AppTab.Map, tabs.selected)
            assertTrue(reselected.isEmpty())
            collecting.cancel()
        }

    @Test
    fun `a tap on the selected tab is a re-tap for that tab - and keeps it selected`() =
        runTest {
            val tabs = AppTabsState(initial = AppTab.MyTrips, splashDone = true)
            val reselected = mutableListOf<AppTab>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tabs.reselected.toList(reselected) }

            tabs.onTabTapped(AppTab.MyTrips)

            assertEquals(listOf(AppTab.MyTrips), reselected)
            assertEquals(AppTab.MyTrips, tabs.selected)
        }

    @Test
    fun `a re-tap pops to the root - or on the root scrolls to the top`() {
        assertEquals(ReselectAction.PopToRoot, AppTabsState.reselectAction(atRoot = false))
        assertEquals(ReselectAction.ScrollToTop, AppTabsState.reselectAction(atRoot = true))
    }

    @Test
    fun `the bar hides on a pushed screen - and follows the selected tab's own stack`() {
        val tabs = AppTabsState(splashDone = true)

        // A trip page pushed in Home: hidden.
        tabs.reportAtRoot(AppTab.Home, false)
        assertFalse(tabs.state.value.barVisible)

        // Map is on its root: switching there shows the bar; back to Home (trip still open) hides it.
        tabs.select(AppTab.Map)
        assertTrue(tabs.state.value.barVisible)
        tabs.select(AppTab.Home)
        assertFalse(tabs.state.value.barVisible)

        // Back to Home's root.
        tabs.reportAtRoot(AppTab.Home, true)
        assertTrue(tabs.state.value.barVisible)
    }

    @Test
    fun `a tab not shown yet counts as on its root - so its first visit shows the bar`() {
        val tabs = AppTabsState(splashDone = true)

        tabs.select(AppTab.Profile)

        assertTrue(tabs.state.value.barVisible)
    }

    @Test
    fun `Back goes Home from another tab's root only`() {
        val tabs = AppTabsState(splashDone = true)
        assertFalse(tabs.state.value.backGoesHome, "Home's root: Back leaves the app")

        tabs.select(AppTab.Profile)
        assertTrue(tabs.state.value.backGoesHome)

        // A pushed screen in My trips: Back is that tab's own (it pops).
        tabs.select(AppTab.MyTrips)
        tabs.reportAtRoot(AppTab.MyTrips, false)
        assertFalse(tabs.state.value.backGoesHome)
    }

    @Test
    fun `a language switch puts every tab back on its root - Home on splash stays off it`() {
        val tabs = AppTabsState(splashDone = true)
        tabs.reportAtRoot(AppTab.Map, false)
        tabs.reportAtRoot(AppTab.Home, false)

        tabs.resetToRoots()

        assertTrue(AppTab.entries.all { tabs.state.value.atRoot[it] == true })
        val onSplash = AppTabsState()
        onSplash.resetToRoots()
        assertEquals(false, onSplash.state.value.atRoot[AppTab.Home])
    }

    @Test
    fun `splash runs once - a later Home starts on its root`() {
        val tabs = AppTabsState()
        assertFalse(tabs.splashDone)

        tabs.markSplashDone()

        assertTrue(tabs.splashDone)
        assertTrue(AppTabsState(splashDone = true).state.value.barVisible)
    }
}
