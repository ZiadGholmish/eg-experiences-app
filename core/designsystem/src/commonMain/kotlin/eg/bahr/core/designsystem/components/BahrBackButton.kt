package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_back
import org.jetbrains.compose.resources.stringResource

/**
 * The round back button of an app bar on a plain surface (HANDOFF screens 4–5: 38px on
 * `primaryContainer`), laid out at the 44dp touch minimum. Read out as "Back"; the arrow mirrors in
 * RTL by itself. Promoted from booking's top bar when the trip lists (M4-M1b) became its second
 * caller. The trip page's translucent button over a photo is a different style and stays there.
 */
interface BahrBackButton {
    @Immutable
    data class Props(
        val onClick: () -> Unit = {},
    )
}

@Composable
fun BahrBackButton(
    modifier: Modifier = Modifier,
    props: BahrBackButton.Props,
) {
    Box(
        modifier =
            modifier
                .size(BahrSpacing.minTouch)
                .clip(BahrTheme.shapes.full)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(role = Role.Button, onClick = props.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BahrIcons.ArrowBack.outlined(),
            contentDescription = stringResource(Res.string.action_back),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
    }
}
