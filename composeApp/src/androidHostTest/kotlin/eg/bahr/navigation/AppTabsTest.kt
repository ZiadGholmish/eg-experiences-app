package eg.bahr.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import kotlinx.coroutines.flow.filter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * M4-M8, Android's tab host ([AppTabs]) with stand-in NavHosts: each tab keeps its own back stack and
 * scroll position while another tab shows, the bar shows on a tab's root only, a tap in the bar
 * switches tabs and a tap on the selected one is a re-tap. The rules themselves are [AppTabsStateTest].
 *
 * The stand-ins report their root and take re-taps the way [AppNavHost] does, without the features'
 * screens (which need the network and Koin; their own tests cover them).
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the app's own starts Koin, which these stand-in tabs do not need (and which
// Robolectric would start again for every test).
@Config(qualifiers = "en-w360dp-h800dp-xhdpi", application = Application::class)
class AppTabsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tabs = AppTabsState(splashDone = true)

    /** M3-M6a (user, 2026-10-23): Alerts left the bar for a bell on Home; four tabs, in reading order. */
    @Test
    fun `the bar is Home - Map - My trips - Profile`() {
        setContent()

        assertEquals(listOf(AppTab.Home, AppTab.Map, AppTab.MyTrips, AppTab.Profile), AppTab.entries)
        AppTab.entries.forEach { compose.onNodeWithTag(it.testTag).assertIsDisplayed() }
        compose.onNodeWithTag("tab_alerts").assertDoesNotExist()
    }

    @Test
    fun `a tap in the bar switches tabs`() {
        setContent()

        compose.onNodeWithTag(AppTab.Map.testTag).performClick()

        assertEquals(AppTab.Map, tabs.selected)
        compose.onNodeWithTag(AppTab.Map.testTag).assertIsSelected()
        compose.onNodeWithText("Map root").assertIsDisplayed()
    }

    @Test
    fun `the bar hides on a pushed screen - and comes back on the root`() {
        setContent()

        compose.onNodeWithText("Open Home detail").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Home detail").assertIsDisplayed()
        compose.onNodeWithTag(AppTab.Home.testTag).assertDoesNotExist()

        compose.onNodeWithText("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(AppTab.Home.testTag).assertIsDisplayed()
    }

    @Test
    fun `each tab keeps its back stack while another tab shows`() {
        setContent()
        compose.onNodeWithText("Open Home detail").performClick()
        compose.waitForIdle()

        // The bar is hidden on the pushed page; a deep link or Home's pill switches tabs the same way.
        compose.runOnUiThread { tabs.select(AppTab.Profile) }
        compose.onNodeWithText("Profile root").assertIsDisplayed()
        compose.runOnUiThread { tabs.select(AppTab.Home) }

        compose.onNodeWithText("Home detail").assertIsDisplayed()
    }

    @Test
    fun `each tab keeps its scroll position while another tab shows`() {
        setContent()
        compose.onNodeWithTag(AppTab.MyTrips.testTag).performClick()
        compose.onNodeWithTag(listTag(AppTab.MyTrips)).performScrollToIndex(ROW_FAR_DOWN)
        compose.onNodeWithText("MyTrips row $ROW_FAR_DOWN").assertIsDisplayed()

        compose.onNodeWithTag(AppTab.Map.testTag).performClick()
        compose.onNodeWithTag(AppTab.MyTrips.testTag).performClick()

        compose.onNodeWithText("MyTrips row $ROW_FAR_DOWN").assertIsDisplayed()
        compose.onNodeWithText("MyTrips row 0").assertDoesNotExist()
    }

    @Test
    fun `a re-tap of the selected tab on its root scrolls back to the top`() {
        setContent()
        compose.onNodeWithTag(listTag(AppTab.Home)).performScrollToIndex(ROW_FAR_DOWN)

        compose.onNodeWithTag(AppTab.Home.testTag).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Home row 0").assertIsDisplayed()
        assertEquals(AppTab.Home, tabs.selected)
    }

    @Test
    fun `a re-tap off the root pops back to it`() {
        setContent()
        compose.onNodeWithTag(AppTab.Map.testTag).performClick()
        compose.onNodeWithText("Open Map detail").performClick()
        compose.waitForIdle()

        // The bar is hidden here, so this is the re-tap Swift reports on iOS, or a test's stand-in for it.
        compose.runOnUiThread { tabs.onTabTapped(AppTab.Map) }
        compose.waitForIdle()

        compose.onNodeWithText("Map root").assertIsDisplayed()
        compose.onNodeWithTag(AppTab.Map.testTag).assertIsDisplayed()
    }

    @Test
    fun `Back on another tab's root goes Home - Back on Home's root is left to the system`() {
        setContent()
        compose.onNodeWithTag(AppTab.Profile.testTag).performClick()
        compose.waitForIdle()

        backPress()

        assertEquals(AppTab.Home, tabs.selected)
        compose.onNodeWithText("Home root").assertIsDisplayed()
        assertEquals(false, compose.activity.onBackPressedDispatcher.hasEnabledCallbacks(), "Home's root: Back leaves the app")
    }

    @Test
    fun `Back off a root pops that tab's own stack`() {
        setContent()
        compose.onNodeWithTag(AppTab.Map.testTag).performClick()
        compose.onNodeWithText("Open Map detail").performClick()
        compose.waitForIdle()

        backPress()

        assertEquals(AppTab.Map, tabs.selected)
        compose.onNodeWithText("Map root").assertIsDisplayed()
    }

    private fun backPress() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    // ---------- Plumbing ----------

    private fun setContent(language: AppLanguage = AppLanguage.ENGLISH) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    AppTabs(tabs = tabs, appVersion = "0.1", content = { tab -> StandInTab(tab) }, overlay = {})
                }
            }
        }
    }

    /** A root with a long list and a way to a detail page, reporting and re-tapping like AppNavHost. */
    @Composable
    private fun StandInTab(tab: AppTab) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val atRoot = entry?.destination?.route?.let { it == ROOT }
        SideEffect(atRoot) { atRoot?.let { tabs.reportAtRoot(tab, it) } }
        val list = rememberLazyListState()
        LaunchedEffect(nav) {
            tabs.reselected.filter { it == tab }.collect {
                when (AppTabsState.reselectAction(atRoot = nav.currentDestination?.route == ROOT)) {
                    AppTabsState.ReselectAction.PopToRoot -> nav.popBackStack(ROOT, inclusive = false)
                    AppTabsState.ReselectAction.ScrollToTop -> list.scrollToItem(0)
                }
            }
        }
        NavHost(nav, startDestination = ROOT) {
            composable(ROOT) {
                Column {
                    Text("${tab.name} root")
                    Button(onClick = { nav.navigate(DETAIL) }) { Text("Open ${tab.name} detail") }
                    LazyColumn(state = list, modifier = Modifier.testTag(listTag(tab))) {
                        items(ROWS) { Text("${tab.name} row $it") }
                    }
                }
            }
            composable(DETAIL) {
                Column {
                    Text("${tab.name} detail")
                    Button(onClick = { nav.popBackStack() }) { Text("Back") }
                }
            }
        }
    }

    private fun listTag(tab: AppTab) = "list_${tab.name}"

    private companion object {
        const val ROOT = "root"
        const val DETAIL = "detail"
        const val ROWS = 60
        const val ROW_FAR_DOWN = 40
    }
}
