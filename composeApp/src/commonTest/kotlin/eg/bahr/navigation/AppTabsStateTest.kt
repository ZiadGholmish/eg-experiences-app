package eg.bahr.navigation

import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.navigation.AppTabsState.ReselectAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    fun `splash runs once - a later Home starts on its root`() {
        val tabs = AppTabsState()
        assertFalse(tabs.splashDone)

        tabs.markSplashDone()

        assertTrue(tabs.splashDone)
        assertTrue(AppTabsState(splashDone = true).state.value.barVisible)
    }

    /**
     * MV-M8, the user's bug: a `?lang=` link rebuilds Home's NavHost, which reports its root and then the linked trip on
     * top. The language's own news may reach the tab state before or after those reports (the old code then marked
     * every tab on its root, after the trip had opened: the bar over the trip page). Either order ends hidden.
     */
    @Test
    fun `a language switch never shows the bar over a trip page opened in the new language - in either order`() {
        // The language arrives first.
        val first = AppTabsState(splashDone = true).apply { onLanguage(AppLanguage.ARABIC) }
        first.onLanguage(AppLanguage.ENGLISH)
        first.reportAtRoot(AppTab.Home, true, AppLanguage.ENGLISH)
        first.reportAtRoot(AppTab.Home, false, AppLanguage.ENGLISH)
        assertFalse(first.state.value.barVisible)

        // The rebuilt NavHost's reports arrive first.
        val last = AppTabsState(splashDone = true).apply { onLanguage(AppLanguage.ARABIC) }
        last.reportAtRoot(AppTab.Home, true, AppLanguage.ENGLISH)
        last.reportAtRoot(AppTab.Home, false, AppLanguage.ENGLISH)
        last.onLanguage(AppLanguage.ENGLISH)
        assertFalse(last.state.value.barVisible)
    }

    /**
     * A language switch rebuilds every tab's NavHost at its root, but an iOS tab not on screen draws (and so reports)
     * only when shown again: its report from the earlier language counts as on its root, so its next visit does not
     * hide and re-show the bar (M4-M8 review #7). Home still on splash stays off its root.
     */
    @Test
    fun `after a language switch a tab not redrawn yet counts as on its root - Home on splash stays off it`() {
        val tabs = AppTabsState(splashDone = true).apply { onLanguage(AppLanguage.ARABIC) }
        tabs.reportAtRoot(AppTab.Map, false, AppLanguage.ARABIC)
        assertFalse(tabs.state.value.barVisible(AppTab.Map))

        tabs.onLanguage(AppLanguage.ENGLISH)

        assertTrue(AppTab.entries.all { tabs.state.value.barVisible(it) })
        val onSplash = AppTabsState().apply { onLanguage(AppLanguage.ARABIC) }
        onSplash.reportAtRoot(AppTab.Home, false, AppLanguage.ARABIC)
        onSplash.onLanguage(AppLanguage.ENGLISH)
        assertFalse(onSplash.state.value.atRoot(AppTab.Home))
    }

    /**
     * MV-M8: an iOS tab that was not redrawn after a switch and back (ar → en → ar) still has the stack it reported in
     * Arabic (its NavHost was never rebuilt), so that report counts again. Android, whose tab host drops those stacks,
     * forgets the reports instead (next test; AppTabsTest checks it on the real host).
     */
    @Test
    fun `a report in the language the app is back in counts again`() {
        val tabs = AppTabsState(splashDone = true).apply { onLanguage(AppLanguage.ARABIC) }
        tabs.reportAtRoot(AppTab.Map, false, AppLanguage.ARABIC)

        tabs.onLanguage(AppLanguage.ENGLISH)
        tabs.onLanguage(AppLanguage.ARABIC)

        assertFalse(tabs.state.value.barVisible(AppTab.Map))
    }

    /** MV-M8 round 1: Android's rebuilt tab host forgets every report but the tab it composed, whose NavHost is live. */
    @Test
    fun `a tab host started over forgets the other tabs' stacks - not the one it shows`() {
        val tabs = AppTabsState(initial = AppTab.Profile, splashDone = true).apply { onLanguage(AppLanguage.ARABIC) }
        tabs.reportAtRoot(AppTab.MyTrips, false, AppLanguage.ARABIC)
        tabs.reportAtRoot(AppTab.Profile, false, AppLanguage.ARABIC)

        tabs.forgetStacks(except = AppTab.Profile)

        assertTrue(tabs.state.value.barVisible(AppTab.MyTrips), "My trips starts at its root")
        assertFalse(tabs.state.value.barVisible(AppTab.Profile), "Profile's live report stands")
    }

    /** MV-M8 (MV-M3's viewer): open and close on a trip page leave the bar hidden; on a root it shows again. */
    @Test
    fun `the viewer hides the bar while open - and closing it on a trip page keeps it hidden`() {
        val tabs = AppTabsState(splashDone = true)
        tabs.reportAtRoot(AppTab.Home, false)

        tabs.reportViewerOpen(AppTab.Home, true)
        assertFalse(tabs.state.value.barVisible)
        assertTrue(tabs.state.value.fullScreen)
        tabs.reportViewerOpen(AppTab.Home, false)
        assertFalse(tabs.state.value.barVisible, "the trip page is still on top")
        assertFalse(tabs.state.value.fullScreen)

        // Back to the root: the bar shows; a viewer opened there hides it until it closes.
        tabs.reportAtRoot(AppTab.Home, true)
        assertTrue(tabs.state.value.barVisible)
        tabs.reportViewerOpen(AppTab.Home, true)
        assertFalse(tabs.state.value.barVisible)
        tabs.reportViewerOpen(AppTab.Home, false)
        assertTrue(tabs.state.value.barVisible)
    }

    /**
     * MV-M8: the viewer is the tab's own. One left open in Map when a link switches to Home does not hide Home's bar or
     * its status bar (MV-M3 had one flag for every tab), and Map's comes back with it.
     */
    @Test
    fun `a viewer open in another tab does not hide the selected tab's bar`() {
        val tabs = AppTabsState(initial = AppTab.Map, splashDone = true)
        tabs.reportAtRoot(AppTab.Map, false)
        tabs.reportViewerOpen(AppTab.Map, true)

        tabs.select(AppTab.Home)
        assertTrue(tabs.state.value.barVisible)
        assertFalse(tabs.state.value.fullScreen)

        tabs.select(AppTab.Map)
        assertFalse(tabs.state.value.barVisible)
        assertTrue(tabs.state.value.fullScreen)
    }

    /** MV-M8: another tab's reports never change the selected tab's bar (each tab writes its own flag only). */
    @Test
    fun `other tabs' reports leave the selected tab's bar alone`() {
        val tabs = AppTabsState(splashDone = true)
        tabs.reportAtRoot(AppTab.Home, false)

        AppTab.entries.filter { it != AppTab.Home }.forEach { other ->
            tabs.reportAtRoot(other, true)
            tabs.reportViewerOpen(other, true)
            tabs.reportViewerOpen(other, false)
            assertFalse(tabs.state.value.barVisible, "after ${other.name}'s reports")
        }
    }

    /** MV-M8: the back stack's every change is reported, in order, and a repeat of the same answer is not sent again. */
    @Test
    fun `following a back stack reports each change of its top`() =
        runTest {
            val tabs = AppTabsState(splashDone = true)
            val stack = MutableStateFlow(true)
            val seen = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                tabs.state
                    .map { it.barVisible }
                    .distinctUntilChanged()
                    .collect { seen += it }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tabs.followBackStack(AppTab.Home, stack, null) }

            stack.value = false
            stack.value = true

            assertEquals(listOf(true, false, true), seen)
        }
}
