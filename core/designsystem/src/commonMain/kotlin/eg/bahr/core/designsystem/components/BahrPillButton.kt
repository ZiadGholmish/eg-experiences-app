package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme

/**
 * A small icon + label pill in an app bar, on `primaryContainer` (HANDOFF Home: the app bar's
 * `map` + "Map" button; the map screen's way back, "List"). A way around the app, not the goal of
 * the screen, so never coral. At least the 44dp touch height. Promoted when the map screen (M4-M2)
 * became its second caller next to Home.
 */
interface BahrPillButton {
    @Immutable
    data class Props(
        val text: String,
        val icon: BahrIcons,
        val onClick: () -> Unit = {},
    )
}

@Composable
fun BahrPillButton(
    modifier: Modifier = Modifier,
    props: BahrPillButton.Props,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = BahrSpacing.minTouch)
                .clip(BahrTheme.shapes.full)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(role = Role.Button, onClick = props.onClick)
                .padding(horizontal = BahrSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        Icon(
            imageVector = props.icon.outlined(),
            // The label says it; the icon would only repeat it.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
        Text(
            text = props.text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
