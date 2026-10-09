package eg.bahr.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------- Shape — mirrors sys.shape ----------
@Immutable
data class BahrShapes(
    val extraSmall: RoundedCornerShape = RoundedCornerShape(6.dp),
    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(16.dp),
    val large: RoundedCornerShape = RoundedCornerShape(18.dp),
    val extraLarge: RoundedCornerShape = RoundedCornerShape(20.dp),
    val card: RoundedCornerShape = RoundedCornerShape(22.dp),
    val hero: RoundedCornerShape = RoundedCornerShape(24.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    /** 100px in tokens.json; Compose clamps a radius to half the side, so this is always a pill. */
    val full: RoundedCornerShape = RoundedCornerShape(100.dp),
)

internal fun BahrShapes.toMaterial() =
    Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge,
    )

// ---------- Spacing — mirrors sys.spacing: 4dp base, 18dp screen gutter ----------
object BahrSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val gutter = 18.dp
    val xl = 22.dp
    val xxl = 26.dp

    /** Not in tokens.json: the minimum touch target the prototype's stepper and chips use. */
    val minTouch = 44.dp
}

/* ---------- Elevation — mirrors sys.elevation ----------
 * CSS shadows are tinted (coral under the CTA, teal under a selected chip) and have a blur radius
 * and offset Compose cannot express. `Modifier.shadow(elevation, shape, ambientColor, spotColor)`
 * with these values is the closest match; the alpha of each untinted level is the token's own.
 */
enum class BahrElevation(
    val dp: Dp,
    internal val inkAlpha: Float,
    internal val outlinedInHighContrast: Boolean,
) {
    Level1(6.dp, inkAlpha = .08f, outlinedInHighContrast = true),
    Level2(10.dp, inkAlpha = .09f, outlinedInHighContrast = true),
    Level3(12.dp, inkAlpha = .12f, outlinedInHighContrast = true),
    Sticky(8.dp, inkAlpha = .08f, outlinedInHighContrast = false),

    /** Tint and alpha come from [BahrExtendedColors.ctaShadow]. */
    Cta(12.dp, inkAlpha = 0f, outlinedInHighContrast = false),

    /** Tint and alpha come from [BahrExtendedColors.chipShadow]. */
    Chip(6.dp, inkAlpha = 0f, outlinedInHighContrast = false),
}

fun Modifier.bahrShadow(
    level: BahrElevation,
    shape: Shape,
    colors: BahrExtendedColors,
): Modifier {
    // HighContrast draws a 1dp outline instead of level1..3 (see BahrCard); sticky keeps its shadow.
    if (colors.outlinedElevation && level.outlinedInHighContrast) return this
    val tint: Color =
        when (level) {
            BahrElevation.Cta -> colors.ctaShadow
            BahrElevation.Chip -> colors.chipShadow
            else -> colors.shadowInk.copy(alpha = level.inkAlpha)
        }
    return shadow(level.dp, shape, clip = false, ambientColor = tint, spotColor = tint)
}

// ---------- Motion — mirrors sys.motion ----------
object BahrMotion {
    const val Short = 150
    const val Medium = 250
    const val Long = 400
    val Standard = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(.05f, .7f, .1f, 1f)
}

/* ---------- Borders ----------
 * tokens.json has no border-width token. The one width the design uses is HighContrast's
 * `0 0 0 1px` outline, named here so no screen spells it out.
 */
object BahrBorder {
    val hairline = 1.dp
}

internal val LocalBahrShapes = staticCompositionLocalOf { BahrShapes() }
