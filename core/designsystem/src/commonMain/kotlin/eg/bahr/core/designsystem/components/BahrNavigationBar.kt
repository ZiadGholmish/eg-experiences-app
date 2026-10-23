package eg.bahr.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.nav_badge_more_than
import eg.bahr.core.localization.generated.resources.nav_item_unread
import org.jetbrains.compose.resources.stringResource

/**
 * The app's bottom bar (M3-M0b, handoff §1, option 1b): a Material 3 `NavigationBar`,
 * 80dp ([BahrSize.navBar]) on `navSurface` under a 1dp `outlineVariant` rule, one item per tab. The
 * selected item has its filled icon in `onPrimaryContainer` on the 64x32 `navIndicator` pill and its
 * label at 800 in `onSurface`; the others are outlined in `onSurfaceVariant` with a 600 label. Labels
 * always show. A way around the app, so never coral. (M4-M8's per-tab colours went with this design.)
 *
 * An item may carry an unread count ([Item.badgeCount]): a [BahrCountBadge] on the icon's top-end corner
 * (it mirrors in RTL), "1"–"9", then "9+". The pill is hidden from screen readers; the item's label
 * carries the count instead ("Alerts, 3 unread"; 9+ is read as "more than 9"), so it is heard once.
 *
 * Edge to edge: the bar draws under the gesture area and pads its items above it (M3's default
 * window insets). In RTL the items run from the right, because the bar is a row and rows mirror;
 * callers list the tabs in reading order and never reverse them by language.
 *
 * Android's bar. iOS keeps its native SwiftUI `TabView` (user decision 2026-10-14), restyled to the same
 * tabs and badge, which the shared code feeds with [badgeText] and [bahrNavigationSpokenLabel].
 *
 * Here rather than private to its one caller (the app module's tab host): the app module cannot run
 * screenshot tests, and the bar's ar/en × light/dark goldens are part of the step.
 */
interface BahrNavigationBar {
    @Immutable
    data class Item(
        val label: String,
        val icon: BahrIcons,
        val selected: Boolean,
        /** For tests and UI automation; the label is what people and screen readers get. */
        val testTag: String? = null,
        /** Unread items to show on the icon; null or 0 shows none. */
        val badgeCount: Int? = null,
    )

    @Immutable
    data class Props(
        val items: List<Item>,
        /** The index of the tapped item, the selected one included (a re-tap means "back to the top"). */
        val onSelect: (index: Int) -> Unit = {},
        /** Whether the bar pads itself above the system's navigation area (off in tests and previews). */
        val windowInsets: Boolean = true,
    )

    companion object {
        /** Counts above this show as "9+" (handoff §1). */
        const val BADGE_MAX = 9

        /** What the badge shows for [count], or null for no badge. */
        fun badgeText(count: Int?): String? =
            when {
                count == null || count <= 0 -> null
                count > BADGE_MAX -> "$BADGE_MAX+"
                else -> count.toString()
            }
    }
}

@Composable
fun BahrNavigationBar(
    modifier: Modifier = Modifier,
    props: BahrNavigationBar.Props,
) {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    Column(modifier) {
        HorizontalDivider(thickness = BahrBorder.hairline, color = c.outlineVariant)
        NavigationBar(
            containerColor = x.navSurface,
            contentColor = c.onSurfaceVariant,
            tonalElevation = NavigationBarDefaults.Elevation,
            windowInsets = if (props.windowInsets) NavigationBarDefaults.windowInsets else WindowInsets(0),
            modifier = Modifier.heightIn(min = BahrSize.navBar),
        ) {
            props.items.forEachIndexed { index, item ->
                val description = item.spokenLabel()
                NavigationBarItem(
                    selected = item.selected,
                    onClick = { props.onSelect(index) },
                    icon = {
                        // M3 hides the icon from screen readers when a label shows; the label speaks.
                        BadgedBox(badge = { BahrCountBadge(props = BahrCountBadge.Props(count = item.badgeCount, ring = x.navSurface)) }) {
                            Icon(
                                imageVector = if (item.selected) item.icon.filled() else item.icon.outlined(),
                                contentDescription = null,
                                modifier = Modifier.size(BahrSize.iconNav),
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (item.selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Read once, with the count in it: "Alerts, 3 unread".
                            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = c.onPrimaryContainer,
                            selectedTextColor = c.onSurface,
                            indicatorColor = x.navIndicator,
                            unselectedIconColor = c.onSurfaceVariant,
                            unselectedTextColor = c.onSurfaceVariant,
                        ),
                    modifier = item.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                )
            }
        }
    }
}

/** The label a screen reader hears: the tab's name, then its unread count if it has one. */
@Composable
private fun BahrNavigationBar.Item.spokenLabel(): String = bahrNavigationSpokenLabel(label, badgeCount)

/**
 * What a screen reader should say for a bar item [label] with [badgeCount] unread (handoff §1): the name
 * alone, or "Alerts, 3 unread" / "الإشعارات، 3 جديدة", 9+ as "more than 9". Public for iOS's native tab
 * bar (M3-M0b), which the shared code hands the same words.
 */
@Composable
fun bahrNavigationSpokenLabel(
    label: String,
    badgeCount: Int?,
): String {
    val shown = BahrNavigationBar.badgeText(badgeCount) ?: return label
    val count = if ((badgeCount ?: 0) > BahrNavigationBar.BADGE_MAX) stringResource(Res.string.nav_badge_more_than) else shown
    return stringResource(Res.string.nav_item_unread, label, count)
}
