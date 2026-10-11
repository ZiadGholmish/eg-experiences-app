package eg.bahr.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eg.bahr.core.designsystem.components.BahrNavigationBar
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.bahrTween
import org.jetbrains.compose.resources.stringResource

/**
 * The tab host where the bar is drawn in Compose (Android; iOS draws SwiftUI's `TabView` and hosts
 * [AppNavHost] per tab itself). M4-M8.
 *
 * Only the selected tab's NavHost is composed. The others are kept by a [rememberSaveableStateHolder]:
 * each tab's NavController (its back stack, view models included) and its screens' saveable state
 * (scroll positions) are saved when the tab is left and restored when it comes back. Edge to edge: the
 * bar pads itself above the gesture area, and the space it takes is consumed from the window insets
 * below it, so a screen's own navigation-bar padding is not added a second time; with the bar hidden
 * (a pushed screen) the screens get the whole window, as before tabs.
 */
@Composable
fun AppTabs(
    tabs: AppTabsState,
    appVersion: String,
    modifier: Modifier = Modifier,
    // The tab's content; a test passes stand-in NavHosts to check what the host keeps.
    content: @Composable (tab: AppTab) -> Unit = { tab -> AppNavHost(tab = tab, tabs = tabs, appVersion = appVersion) },
) {
    val state by tabs.state.collectAsStateWithLifecycle()
    val holder = rememberSaveableStateHolder()

    // Back on the root of Search, Map or Settings goes to Home; on Home's root it leaves the app. Off a
    // root the tab's NavHost handles Back (its own handler is the enabled one there).
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.backGoesHome,
        onBackCompleted = { tabs.select(AppTab.Home) },
    )

    Scaffold(
        modifier = modifier,
        // Each screen handles its own top inset (some run full-bleed under the status bar).
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Slides away with a pushed screen and back with its root (instant with reduced motion).
            AnimatedVisibility(
                visible = state.barVisible,
                enter = expandVertically(animationSpec = bahrTween(BahrMotion.Medium)),
                exit = shrinkVertically(animationSpec = bahrTween(BahrMotion.Medium)),
            ) {
                BahrNavigationBar(
                    props =
                        BahrNavigationBar.Props(
                            items =
                                AppTab.entries.map { tab ->
                                    BahrNavigationBar.Item(
                                        label = stringResource(tab.title),
                                        icon = tab.icon,
                                        selected = tab == state.selected,
                                        testTag = tab.testTag,
                                        tone = tab.tone,
                                    )
                                },
                            onSelect = { index -> tabs.onTabTapped(AppTab.entries[index]) },
                        ),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            holder.SaveableStateProvider(state.selected) { content(state.selected) }
        }
    }
}
