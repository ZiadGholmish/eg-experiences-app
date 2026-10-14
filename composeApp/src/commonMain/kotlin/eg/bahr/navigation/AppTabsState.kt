package eg.bahr.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which tab shows, and whether the bar does (M4-M8). Plain state, no Compose, so the rules are unit
 * tests, and the same object drives both bars: Android's Compose bar ([AppTabs]) reads it directly; on iOS
 * the shared code mirrors it to the SwiftUI `TabView` (`IosTabs`) and Swift reports taps back.
 *
 * - A tap on another tab selects it; a tap on the selected one is a re-tap, sent on [reselected] to
 *   that tab's NavHost, which pops to its root or, already there, scrolls to the top ([reselectAction]).
 * - The bar shows on a tab root only: each tab's NavHost reports whether its root is on top
 *   ([reportAtRoot]), and the bar follows the selected tab's answer. Splash is not a root.
 *
 * (Home's search entry no longer switches tabs: since M3-M0b search is a page pushed in the tab that
 * opened it, Home or Map.)
 */
class AppTabsState(
    initial: AppTab = AppTab.Home,
    splashDone: Boolean = false,
) {
    /** One snapshot, so the bar never sees a selection and a root flag from two different moments. */
    data class Snapshot(
        val selected: AppTab,
        val atRoot: Map<AppTab, Boolean>,
        val splashDone: Boolean,
    ) {
        /** The bar shows on the selected tab's root only (hidden on the trip page, booking, hold, confirmation). */
        val barVisible: Boolean get() = atRoot[selected] == true

        /**
         * Android's Back on the root of a tab other than Home goes to Home rather than leaving the app
         * (user decision 2026-10-11); on Home's root, or off any root, Back does what it always did.
         */
        val backGoesHome: Boolean get() = selected != AppTab.Home && barVisible
    }

    private val _state =
        MutableStateFlow(
            Snapshot(
                selected = initial,
                // Until a tab's NavHost reports, its root is assumed on top, so a first visit does not
                // flash the bar away; Home starts on splash, which is not a root, until splash is done.
                atRoot = rootsAtStart(splashDone),
                splashDone = splashDone,
            ),
        )
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    val selected: AppTab get() = _state.value.selected

    val splashDone: Boolean get() = _state.value.splashDone

    private val _reselected = MutableSharedFlow<AppTab>(extraBufferCapacity = EVENT_BUFFER)

    /** Re-taps of the selected tab, for that tab's NavHost. */
    val reselected: SharedFlow<AppTab> = _reselected.asSharedFlow()

    /** A tap on a tab in the bar. */
    fun onTabTapped(tab: AppTab) {
        if (tab == selected) _reselected.tryEmit(tab) else select(tab)
    }

    /** Shows [tab] without it counting as a tap: Home's Map pill, a deep link (Home). */
    fun select(tab: AppTab) {
        _state.update { it.copy(selected = tab) }
    }

    fun reportAtRoot(
        tab: AppTab,
        atRoot: Boolean,
    ) {
        _state.update { it.copy(atRoot = it.atRoot + (tab to atRoot)) }
    }

    /**
     * A language switch rebuilds every tab's NavHost at its root, including tabs not shown now, which
     * cannot report it until they are shown again: mark them all on their root, so their next visit does
     * not hide and re-show the bar (M4-M8 review #7). Home still on splash stays off its root.
     */
    fun resetToRoots() {
        _state.update { it.copy(atRoot = rootsAtStart(it.splashDone)) }
    }

    /** Splash runs once per process: a later Home NavHost (after a language switch) starts on Home itself. */
    fun markSplashDone() {
        _state.update { it.copy(splashDone = true) }
    }

    /** What a re-tap of the selected tab does. */
    enum class ReselectAction { PopToRoot, ScrollToTop }

    companion object {
        /** Off the root, a re-tap goes back to it; on the root, back to the top of its list. */
        fun reselectAction(atRoot: Boolean): ReselectAction = if (atRoot) ReselectAction.ScrollToTop else ReselectAction.PopToRoot

        private fun rootsAtStart(splashDone: Boolean): Map<AppTab, Boolean> =
            AppTab.entries.associateWith { it != AppTab.Home || splashDone }

        /** Taps are rare; a few buffered cover a fast double tap while the NavHost is busy. */
        private const val EVENT_BUFFER = 4
    }
}
