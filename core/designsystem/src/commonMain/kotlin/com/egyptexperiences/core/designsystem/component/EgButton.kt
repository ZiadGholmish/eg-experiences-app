package com.egyptexperiences.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.egyptexperiences.core.designsystem.theme.EgOnBrand
import com.egyptexperiences.core.designsystem.theme.EgTeal
import com.egyptexperiences.core.designsystem.theme.EgTheme

/**
 * The one primary action per screen: full width, pill, teal.
 *
 * [loading] disables the button as well as swapping the label, so a
 * double-tap cannot place two holds on the same departure.
 */
@Composable
fun EgPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = EgTheme.shapes.pill,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = EgTeal,
                contentColor = EgOnBrand,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.heightIn(min = 20.dp),
                    strokeWidth = 2.dp,
                    color = EgOnBrand,
                )
            } else {
                Text(text = text, style = EgTheme.typography.button)
            }
        }
    }
}
