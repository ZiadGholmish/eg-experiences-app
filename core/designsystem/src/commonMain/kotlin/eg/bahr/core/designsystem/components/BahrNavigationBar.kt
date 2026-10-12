package eg.bahr.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize

/**
 * The app's bottom bar on Android (M4-M8): a Material 3 `NavigationBar` in Bahr colours, one item per
 * tab. Each item may carry a tone (the badge palette): selected, its filled icon and its label take the
 * tone's content colour, on the tone's container pill; unselected items are outlined and muted, so the
 * bar stays calm. A way around the app, so never coral.
 *
 * Edge to edge: the bar draws under the gesture area and pads its items above it (M3's default
 * window insets). In RTL the items run from the right, because the bar is a row and rows mirror;
 * callers list the tabs in reading order and never reverse them by language.
 *
 * Here rather than private to its one caller (the app module's tab host): the app module cannot run
 * screenshot tests, and the bar's ar/en goldens are part of the step. iOS draws its own bar (SwiftUI's
 * `TabView`) and never uses this.
 */
interface BahrNavigationBar {
    @Immutable
    data class Item(
        val label: String,
        val icon: BahrIcons,
        val selected: Boolean,
        /** For tests and UI automation; the label is what people and screen readers get. */
        val testTag: String? = null,
        /**
         * The item's own colour when selected (M4-M8): its icon and label in the tone's content colour
         * on the tone's container pill. Null: the neutral secondary pill.
         */
        val tone: BahrBadge.Tone? = null,
    )

    @Immutable
    data class Props(
        val items: List<Item>,
        /** The index of the tapped item, the selected one included (a re-tap means "back to the top"). */
        val onSelect: (index: Int) -> Unit = {},
    )
}

@Composable
fun BahrNavigationBar(
    modifier: Modifier = Modifier,
    props: BahrNavigationBar.Props,
) {
    NavigationBar(
        modifier = modifier,
        // Bars are `surface` white over the off-white page (M4-M12), as cards and sheets are.
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        props.items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = item.selected,
                onClick = { props.onSelect(index) },
                icon = {
                    Icon(
                        imageVector = if (item.selected) item.icon.filled() else item.icon.outlined(),
                        // The label below says it.
                        contentDescription = null,
                        modifier = Modifier.size(BahrSize.iconLarge),
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = item.colors(),
                modifier = item.testTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

/** Selected: the tone's content colour on its container; unselected: muted on-surface. */
@Composable
private fun BahrNavigationBar.Item.colors(): NavigationBarItemColors {
    val (container, content) =
        tone?.colors() ?: (MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer)
    return NavigationBarItemDefaults.colors(
        selectedIconColor = content,
        selectedTextColor = content,
        indicatorColor = container,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
