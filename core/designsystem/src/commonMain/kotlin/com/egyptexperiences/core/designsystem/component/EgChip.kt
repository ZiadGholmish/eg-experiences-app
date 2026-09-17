package com.egyptexperiences.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.egyptexperiences.core.designsystem.theme.EgOnBrand
import com.egyptexperiences.core.designsystem.theme.EgTeal
import com.egyptexperiences.core.designsystem.theme.EgTealTint
import com.egyptexperiences.core.designsystem.theme.EgTheme

/** Category filter on the home screen. Selected inverts tint and text. */
@Composable
fun EgFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = EgTheme.typography.chip,
        color = if (selected) EgOnBrand else EgTeal,
        modifier =
            modifier
                .clip(EgTheme.shapes.pill)
                .background(if (selected) EgTeal else EgTealTint)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = EgTheme.spacings.xxl,
                    vertical = EgTheme.spacings.lg,
                ),
    )
}
