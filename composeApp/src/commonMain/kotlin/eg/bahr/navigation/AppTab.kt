package eg.bahr.navigation

import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.tab_home
import eg.bahr.core.localization.generated.resources.tab_map
import eg.bahr.core.localization.generated.resources.tab_search
import eg.bahr.core.localization.generated.resources.tab_settings
import org.jetbrains.compose.resources.StringResource

/**
 * The bottom bar's tabs (M4-M8, user decisions 2026-10-11), in reading order: the bar is a row, so in
 * Arabic Home sits at the right without anyone reversing the list. Each tab is its own back stack with
 * its own root: Home (feature:home), Search (feature:trips' search), Map (feature:map) and Settings
 * (feature:settings).
 *
 * Public because Swift names the tabs: iOS draws the bar itself (SwiftUI `TabView`) and asks for one
 * Compose root per tab (`TabViewController(tab:)`).
 */
enum class AppTab {
    Home,
    Search,
    Map,
    Settings,
}

/** The tab's label, from core:localization; iOS gets the resolved strings from the shared code too. */
internal val AppTab.title: StringResource
    get() =
        when (this) {
            AppTab.Home -> Res.string.tab_home
            AppTab.Search -> Res.string.tab_search
            AppTab.Map -> Res.string.tab_map
            AppTab.Settings -> Res.string.tab_settings
        }

/** The Android bar's icon (Material Symbols, filled when selected). iOS uses SF Symbols in Swift. */
internal val AppTab.icon: BahrIcons
    get() =
        when (this) {
            AppTab.Home -> BahrIcons.Home
            AppTab.Search -> BahrIcons.Search
            AppTab.Map -> BahrIcons.Map
            AppTab.Settings -> BahrIcons.Settings
        }

/**
 * The tab's own colour when selected (user request 2026-10-11: colour in the bar), from the badge tone
 * palette: Home teal (primary), Search gold (secondary), Map green (success), Settings magenta
 * (quaternary). Selected, the icon and label take the tone's content colour (on its container pill on
 * Android; as the bar's tint on iOS).
 */
internal val AppTab.tone: BahrBadge.Tone
    get() =
        when (this) {
            AppTab.Home -> BahrBadge.Tone.Primary
            AppTab.Search -> BahrBadge.Tone.Secondary
            AppTab.Map -> BahrBadge.Tone.Success
            AppTab.Settings -> BahrBadge.Tone.Quaternary
        }

/** For tests and UI automation: the Android bar's item for [this] tab. */
internal val AppTab.testTag: String get() = "tab_${name.lowercase()}"
