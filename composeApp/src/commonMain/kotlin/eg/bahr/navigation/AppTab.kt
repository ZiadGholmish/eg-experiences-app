package eg.bahr.navigation

import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.tab_home
import eg.bahr.core.localization.generated.resources.tab_map
import eg.bahr.core.localization.generated.resources.tab_my_trips
import eg.bahr.core.localization.generated.resources.tab_profile
import org.jetbrains.compose.resources.StringResource

/**
 * The bottom bar's tabs, in reading order: the bar is a row, so in Arabic Home sits at the right without
 * anyone reversing the list. Each tab is its own back stack with its own root.
 *
 * M3-M0b (user, 2026-10-14; handoff §1, option 1b): Home (feature:home) · Map (feature:map) · My trips
 * (feature:mytrips) · Alerts (feature:notifications) · Profile (feature:settings, which was the Settings
 * tab). The Search tab went: search opens full screen from Home's search field and the Map header, in
 * that tab's own stack. M3-M6a (user, 2026-10-23): the Alerts tab went too; its page is pushed from a bell
 * in Home's top bar, so the bar is Home · Map · My trips · Profile. Android draws the bar in Compose
 * ([AppTabs]); iOS keeps its native SwiftUI `TabView` (user decision 2026-10-14), fed from here.
 *
 * Public because Swift names the tabs: it asks for one Compose root per tab (`TabViewController(tab:)`).
 */
enum class AppTab {
    Home,
    Map,
    MyTrips,
    Profile,
}

/** The tab's label, from core:localization. */
internal val AppTab.title: StringResource
    get() =
        when (this) {
            AppTab.Home -> Res.string.tab_home
            AppTab.Map -> Res.string.tab_map
            AppTab.MyTrips -> Res.string.tab_my_trips
            AppTab.Profile -> Res.string.tab_profile
        }

/** The bar's icon (Material Symbols, filled when selected): the handoff's `NAVDEF`. */
internal val AppTab.icon: BahrIcons
    get() =
        when (this) {
            AppTab.Home -> BahrIcons.Home
            AppTab.Map -> BahrIcons.Map
            AppTab.MyTrips -> BahrIcons.ConfirmationNumber
            AppTab.Profile -> BahrIcons.Person
        }

/**
 * The unread count on [this] tab's item, for both bars. None since M3-M6a: the unread count moved with
 * Alerts to Home's bell. The bars keep their badge support (handoff §1) for a tab that may need one.
 */
internal val AppTab.badgeCount: Int? get() = null

/** For tests and UI automation: the bar's item for [this] tab. */
internal val AppTab.testTag: String get() = "tab_${name.lowercase()}"
