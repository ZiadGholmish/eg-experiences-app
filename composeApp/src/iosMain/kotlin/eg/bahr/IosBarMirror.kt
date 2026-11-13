package eg.bahr

import eg.bahr.navigation.AppTab
import eg.bahr.navigation.AppTabsState

/**
 * Mirrors each [AppTabsState.Snapshot] to Swift's bar (M4-M8), sending only what changed: the selected tab, each tab's
 * bar (hidden off its root or under its open viewer) and whether the selected tab's viewer is full screen.
 *
 * Everything sent is derived from the snapshot, so a snapshot skipped by the state flow's conflation loses nothing: the
 * next one carries the whole state (MV-M8).
 */
internal class IosBarMirror(
    private val bar: AppTabBar,
) {
    private var selected: AppTab? = null
    private val hidden = mutableMapOf<AppTab, Boolean>()
    private var fullScreen: Boolean? = null

    fun show(now: AppTabsState.Snapshot) {
        if (selected != now.selected) {
            selected = now.selected
            bar.selectTab(now.selected)
        }
        AppTab.entries.forEach { tab ->
            val hide = !now.barVisible(tab)
            if (hidden[tab] != hide) {
                hidden[tab] = hide
                bar.setBarHidden(tab, hide)
            }
        }
        if (fullScreen != now.fullScreen) {
            fullScreen = now.fullScreen
            bar.setFullScreen(now.fullScreen)
        }
    }
}
