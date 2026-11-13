package eg.bahr.navigation

import eg.bahr.core.common.locale.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/**
 * Which tab shows, and whether the bar does (M4-M8). Plain state, no Compose, so the rules are unit
 * tests, and the same object drives both bars: Android's Compose bar ([AppTabs]) reads it directly; on iOS
 * the shared code mirrors it to the SwiftUI `TabView` (`IosTabs`) and Swift reports taps back.
 *
 * - A tap on another tab selects it; a tap on the selected one is a re-tap, sent on [reselected] to
 *   that tab's NavHost, which pops to its root or, already there, scrolls to the top ([reselectAction]).
 * - The bar shows on a tab root only, and not while that tab's photo viewer is open. Both come from the
 *   tab itself: its NavHost follows its own back stack ([followBackStack]) and its viewer host says when it
 *   is open ([reportViewerOpen]). Nothing else writes them (MV-M8: a language switch used to mark every tab
 *   on its root from outside, and when that landed after a trip page had opened, the bar stayed over it).
 *   Splash is not a root.
 *
 * (Home's search entry no longer switches tabs: since M3-M0b search is a page pushed in the tab that
 * opened it, Home or Map.)
 */
class AppTabsState(
    initial: AppTab = AppTab.Home,
    splashDone: Boolean = false,
) {
    /**
     * What a tab's NavHost last said about its back stack, and in which [language] that NavHost was built: a
     * language switch rebuilds every NavHost at its root, so a report from an earlier language is out of date.
     */
    data class StackReport(
        val atRoot: Boolean,
        val language: AppLanguage?,
    )

    /** One snapshot, so the bar never sees a selection and a root flag from two different moments. */
    data class Snapshot(
        val selected: AppTab,
        val splashDone: Boolean,
        /** The app's language; null until it is known, when every report counts. */
        val language: AppLanguage? = null,
        val stacks: Map<AppTab, StackReport> = emptyMap(),
        /** The tabs whose full-screen photo and video viewer (MV-M3) is open, closing fade included. */
        val viewerOpen: Set<AppTab> = emptySet(),
    ) {
        /**
         * Whether [tab]'s root is on top. A tab that has not reported yet, or reported in an earlier language
         * (its NavHost is rebuilt at its root as soon as it draws again), counts as on its root, so a first visit
         * does not flash the bar away; Home starts on splash, which is not a root, until splash is done.
         */
        fun atRoot(tab: AppTab): Boolean {
            val report = stacks[tab]
            val current = report != null && (language == null || report.language == null || report.language == language)
            return if (current) report.atRoot else tab != AppTab.Home || splashDone
        }

        /** The bar over [tab]: on its root only, and never under its open viewer. */
        fun barVisible(tab: AppTab): Boolean = atRoot(tab) && tab !in viewerOpen

        /** The bar shows on the selected tab's root only (hidden on the trip page, booking, hold, confirmation, the viewer). */
        val barVisible: Boolean get() = barVisible(selected)

        /** The selected tab's viewer is open: no status bar or home indicator either (iOS; Android's viewer hides its own). */
        val fullScreen: Boolean get() = selected in viewerOpen

        /**
         * Android's Back on the root of a tab other than Home goes to Home rather than leaving the app
         * (user decision 2026-10-11); on Home's root, or off any root, Back does what it always did.
         */
        val backGoesHome: Boolean get() = selected != AppTab.Home && atRoot(selected)
    }

    private val _state = MutableStateFlow(Snapshot(selected = initial, splashDone = splashDone))
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

    /**
     * [tab]'s NavHost, built in [language], has its root on top or not. Only that NavHost calls it, from its back
     * stack ([followBackStack]).
     */
    fun reportAtRoot(
        tab: AppTab,
        atRoot: Boolean,
        language: AppLanguage? = null,
    ) {
        _state.update { it.copy(stacks = it.stacks + (tab to StackReport(atRoot, language))) }
    }

    /**
     * Reports [tab]'s back stack as it changes, until cancelled: [atRoot] is the top entry's answer, one value per
     * change of the top. Collecting the stack itself, rather than reporting edges from composition, means every
     * change is reported, in order, by the one writer of this tab's flag.
     */
    suspend fun followBackStack(
        tab: AppTab,
        atRoot: Flow<Boolean>,
        language: AppLanguage?,
    ) {
        atRoot.distinctUntilChanged().collect { reportAtRoot(tab, it, language) }
    }

    /**
     * Android's tab host ([AppTabs]) started over without its saved state (a language switch: ProvideAppLanguage keys
     * it), so every tab's stack but the one it composed first, [except], is gone and starts at its root when next
     * shown. Those tabs have no NavHost to report, so this is no second writer; [except]'s live NavHost reports itself.
     * Needed because a language tag alone would make an old report count again after ar → en → ar (MV-M8 round 1). iOS
     * never calls it: there a tab root not redrawn keeps its stack, and its report stays true.
     */
    fun forgetStacks(except: AppTab) {
        _state.update { it.copy(stacks = it.stacks.filterKeys { tab -> tab == except }) }
    }

    /**
     * The app's language now (each value of `AppLanguageState`); NavHosts built in another one are being rebuilt.
     *
     * Its order against the rebuilt NavHost's reports is not guaranteed: if those land first they count as "root" until
     * this arrives (a frame at most, as this is a StateFlow collector on the main thread); either order ends right.
     */
    fun onLanguage(language: AppLanguage) {
        _state.update { it.copy(language = language) }
    }

    /** [tab]'s full-screen viewer opened ([open]) or finished closing. Only that tab's viewer host calls it. */
    fun reportViewerOpen(
        tab: AppTab,
        open: Boolean,
    ) {
        _state.update { it.copy(viewerOpen = if (open) it.viewerOpen + tab else it.viewerOpen - tab) }
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

        /** Taps are rare; a few buffered cover a fast double tap while the NavHost is busy. */
        private const val EVENT_BUFFER = 4
    }
}
