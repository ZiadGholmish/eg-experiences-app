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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme

/*
 * Small parts shared by the trip page's sections. Private to the feature until a second screen needs
 * one (then it moves to core:designsystem).
 */

/** A section heading: the handoff's `title` role (17px / 800). */
@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

/**
 * The handoff's colour tints (its `TINT` table): a container, the text on it, and the icon colour.
 *
 * Coral is the action colour and nothing else (mobile rule 3), so the coral tint uses the coral
 * *container* family only: its icon is `onTertiaryContainer`, not the prototype's `tertiary`.
 */
internal enum class Tint { Teal, Gold, Coral, Magenta, Green }

internal data class TintColors(
    val container: Color,
    val content: Color,
    val icon: Color,
)

@Composable
internal fun Tint.colors(): TintColors {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    return when (this) {
        Tint.Teal -> TintColors(c.primaryContainer, c.onPrimaryContainer, c.primary)
        Tint.Gold -> TintColors(c.secondaryContainer, c.onSecondaryContainer, x.secondaryDim)
        Tint.Coral -> TintColors(c.tertiaryContainer, c.onTertiaryContainer, c.onTertiaryContainer)
        Tint.Magenta -> TintColors(x.quaternaryContainer, x.onQuaternaryContainer, x.quaternary)
        Tint.Green -> TintColors(x.successContainer, x.onSuccessContainer, x.success)
    }
}

/** The contract's lowercase `Tone` as a tint; an unknown tone is teal. */
internal fun toneTint(tone: String?): Tint =
    when (tone) {
        "secondary" -> Tint.Gold
        "tertiary" -> Tint.Coral
        "quaternary" -> Tint.Magenta
        "success" -> Tint.Green
        else -> Tint.Teal
    }

/**
 * A Material Symbols name from the server (`directions_bus`) as one of the design's icons. A name
 * this build does not have is a plain dot rather than a missing glyph.
 */
internal fun symbolIcon(name: String?): BahrIcons =
    when (name) {
        "directions_bus" -> BahrIcons.DirectionsBus
        "local_cafe" -> BahrIcons.LocalCafe
        "sailing" -> BahrIcons.Sailing
        "flutter_dash" -> BahrIcons.FlutterDash
        "restaurant" -> BahrIcons.Restaurant
        "palette" -> BahrIcons.Palette
        "waves" -> BahrIcons.Waves
        "home" -> BahrIcons.Home
        "storefront" -> BahrIcons.Storefront
        "beach_access" -> BahrIcons.BeachAccess
        "bedtime" -> BahrIcons.Bedtime
        "place", "location_on" -> BahrIcons.Place
        // The trip list's filter chips (M4-B1 facets).
        "apps" -> BahrIcons.Apps
        "calendar_month" -> BahrIcons.CalendarMonth
        "sell" -> BahrIcons.Sell
        "schedule" -> BahrIcons.Schedule
        else -> BahrIcons.Circle
    }

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
