package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme

/*
 * Small parts shared by the trip page's sections. Private to the feature until a second screen needs
 * one (then it moves to core:designsystem).
 */

/** A filled circle with an icon in it: itinerary dots, included/excluded markers. */
@Composable
internal fun Marker(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(BahrSize.marker).clip(BahrTheme.shapes.full).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(BahrSize.iconSmall))
    }
}
