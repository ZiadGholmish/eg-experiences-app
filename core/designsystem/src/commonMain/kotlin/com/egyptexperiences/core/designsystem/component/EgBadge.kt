package com.egyptexperiences.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.egyptexperiences.core.designsystem.theme.EgCoralDeep
import com.egyptexperiences.core.designsystem.theme.EgCoralTint
import com.egyptexperiences.core.designsystem.theme.EgTheme

/**
 * Scarcity and status marker — "2 seats left", "Sold out", "Verified host".
 *
 * Defaults to the coral tint because the commonest use on the canvas is
 * urgency; pass the teal or success pair for the calmer cases.
 */
@Composable
fun EgBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = EgCoralTint,
    content: Color = EgCoralDeep,
) {
    Text(
        text = text,
        style = EgTheme.typography.badge,
        color = content,
        modifier =
            modifier
                .clip(EgTheme.shapes.pill)
                .background(container)
                .padding(
                    horizontal = EgTheme.spacings.lg,
                    vertical = EgTheme.spacings.xs,
                ),
    )
}
