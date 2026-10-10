package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme

/**
 * A category's tinted tile (HANDOFF Home → category row): its filled [icon] (a Material Symbols name)
 * on the container of its [tone] (the contract's `Tone`). Home's category row draws one per category
 * (M4-M1), and the category page's header the one it is about (M4-M1b). Decorative: the caption or
 * label next to it is what gets read.
 */
@Composable
internal fun CategoryTile(
    icon: String?,
    tone: String?,
    modifier: Modifier = Modifier,
) {
    val tint = toneTint(tone).colors()
    Box(
        modifier =
            modifier
                .size(BahrSize.categoryTile)
                .clip(BahrTheme.shapes.extraLarge)
                .background(tint.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = symbolIcon(icon).filled(),
            contentDescription = null,
            tint = tint.icon,
            modifier = Modifier.size(BahrSize.iconLarge),
        )
    }
}
