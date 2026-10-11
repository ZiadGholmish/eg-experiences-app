package eg.bahr.feature.trips.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import eg.bahr.core.designsystem.theme.BahrMotion
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

/**
 * A placeholder block for what is still loading (photos, the itinerary, live seat counts). The
 * handoff's shimmer: `surfaceDim`, opacity .5 → .9 → .5 over 1.5s.
 */
@Composable
internal fun Skeleton(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition().animateFloat(
        initialValue = BahrMotion.ShimmerAlphaLow,
        targetValue = BahrMotion.ShimmerAlphaHigh,
        animationSpec = infiniteRepeatable(tween(BahrMotion.Shimmer / 2, easing = BahrMotion.EaseInOut), RepeatMode.Reverse),
    )
    Box(modifier.graphicsLayer { this.alpha = alpha }.background(BahrTheme.colors.surfaceDim))
}
