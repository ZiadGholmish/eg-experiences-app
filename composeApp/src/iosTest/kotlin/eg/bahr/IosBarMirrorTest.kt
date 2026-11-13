package eg.bahr

import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.navigation.AppTab
import eg.bahr.navigation.AppTabsState
import platform.UIKit.UIColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MV-M8: what Swift's bar is told. Swift shows the selected tab's value on every tab (TabBarModel.isBarHidden), so the
 * selected tab's flag must be right after every snapshot, and the viewer reaches it through that flag.
 */
class IosBarMirrorTest {
    private val bar = RecordingBar()
    private val tabs = AppTabsState(splashDone = true)
    private val mirror = IosBarMirror(bar)

    private fun mirror() = mirror.show(tabs.state.value)

    @Test
    fun `the first snapshot sends the selection - every tab's bar - and the status bar`() {
        mirror()

        assertEquals(listOf(AppTab.Home), bar.selected)
        assertEquals(AppTab.entries.associateWith { false }, bar.hidden)
        assertEquals(listOf(false), bar.fullScreen)
    }

    @Test
    fun `the viewer opened and closed over a trip page leaves that tab hidden - status bar back`() {
        mirror()
        tabs.reportAtRoot(AppTab.Home, false)
        mirror()
        assertEquals(true, bar.hidden[AppTab.Home])

        tabs.reportViewerOpen(AppTab.Home, true)
        mirror()
        tabs.reportViewerOpen(AppTab.Home, false)
        mirror()

        assertEquals(true, bar.hidden[AppTab.Home], "trip page still on top")
        assertEquals(listOf(false, true, false), bar.fullScreen)
        // Home's hidden flag was sent once: the viewer never flipped it (nor any other tab's).
        assertEquals(1, bar.calls.count { it == AppTab.Home to true })
        assertTrue(bar.calls.none { it.first != AppTab.Home && it.second }, "no other tab hidden: ${bar.calls}")
    }

    @Test
    fun `a viewer opened on a root hides that tab's bar until it closes`() {
        mirror()
        tabs.reportViewerOpen(AppTab.Home, true)
        mirror()
        assertEquals(true, bar.hidden[AppTab.Home])

        tabs.reportViewerOpen(AppTab.Home, false)
        mirror()
        assertEquals(false, bar.hidden[AppTab.Home])
    }

    @Test
    fun `a language switch then a trip opened in the new language - Home stays hidden whatever the order`() {
        tabs.onLanguage(AppLanguage.ARABIC)
        mirror()
        tabs.reportAtRoot(AppTab.Home, true, AppLanguage.ENGLISH)
        tabs.reportAtRoot(AppTab.Home, false, AppLanguage.ENGLISH)
        tabs.onLanguage(AppLanguage.ENGLISH)
        mirror()

        assertEquals(true, bar.hidden[AppTab.Home])
    }

    @Test
    fun `a selection change is sent once`() {
        mirror()
        tabs.select(AppTab.Map)
        mirror()
        mirror()

        assertEquals(listOf(AppTab.Home, AppTab.Map), bar.selected)
    }

    private class RecordingBar : AppTabBar {
        val selected = mutableListOf<AppTab>()
        val hidden = mutableMapOf<AppTab, Boolean>()
        val calls = mutableListOf<Pair<AppTab, Boolean>>()
        val fullScreen = mutableListOf<Boolean>()

        override fun showContent(
            languageTag: String,
            rightToLeft: Boolean,
            titles: List<String>,
            spokenLabels: List<String>,
            badges: List<String>,
            selectedTint: UIColor,
            unselectedTint: UIColor,
        ) = Unit

        override fun applyAppearance(appearance: String) = Unit

        override fun selectTab(tab: AppTab) {
            selected += tab
        }

        override fun setBarHidden(
            tab: AppTab,
            hidden: Boolean,
        ) {
            this.hidden[tab] = hidden
            calls += tab to hidden
        }

        override fun setFullScreen(fullScreen: Boolean) {
            this.fullScreen += fullScreen
        }
    }
}
