package eg.bahr.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.border
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

    /**
     * `elevation.raised` (`0 6px 16px`): the selected availability card (HANDOFF screen 3). Tint and
     * alpha come from [BahrExtendedColors.raisedShadow]. tokens.css does not override it in
     * HighContrast, so it keeps its shadow there.
     */
    Raised(8.dp, inkAlpha = 0f, outlinedInHighContrast = false),

    /**
     * `elevation.float` (`0 2px 8px rgba(0,0,0,.18)`): circular buttons floating over photos. Tint
     * and alpha come from [BahrExtendedColors.floatShadow]; not outlined in HighContrast either.
     */
    Float(4.dp, inkAlpha = 0f, outlinedInHighContrast = false),
}

/**
 * The token's shadow, or under HighContrast (`outlinedElevation`) the 1dp outline tokens.css puts
 * in place of `level1..3`. The outline is drawn here rather than by each caller, so any elevated
 * surface gets it, not only [eg.bahr.core.designsystem.components.BahrCard] (M0-M2 review #5).
 * `border` draws over what follows it in the chain, so the outline sits on top of the surface's
 * background.
 */
fun Modifier.bahrShadow(
    level: BahrElevation,
    shape: Shape,
    colors: BahrExtendedColors,
): Modifier {
    if (colors.outlinedElevation && level.outlinedInHighContrast) {
        return border(BahrBorder.hairline, colors.shadowInk, shape)
    }
    val tint: Color =
        when (level) {
            BahrElevation.Cta -> colors.ctaShadow
            BahrElevation.Chip -> colors.chipShadow
            BahrElevation.Raised -> colors.raisedShadow
            BahrElevation.Float -> colors.floatShadow
            else -> colors.shadowInk.copy(alpha = level.inkAlpha)
        }
    return shadow(level.dp, shape, clip = false, ambientColor = tint, spotColor = tint)
}

// ---------- Motion — mirrors sys.motion ----------
object BahrMotion {
    const val Short = 150
    const val Medium = 250
    const val Long = 400

    /** The loading shimmer's cycle (HANDOFF: `sh` keyframes, opacity .5 → .9 → .5 over 1.5s). */
    const val Shimmer = 1500
    const val ShimmerAlphaLow = .5f
    const val ShimmerAlphaHigh = .9f

    /** CSS `ease-in-out` (`cubic-bezier(.42,0,.58,1)`): the shimmer's easing in the handoff. */
    val EaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
    val Standard = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(.05f, .7f, .1f, 1f)
}

/* ---------- Borders ----------
 * tokens.json has no border-width token. The one width the design uses is HighContrast's
 * `0 0 0 1px` outline, named here so no screen spells it out.
 */
object BahrBorder {
    val hairline = 1.dp

    /** The prototype's `inset 0 0 0 2px` ring on a selected sold-out date card. */
    val selected = 2.dp
}

/* ---------- Sizes ----------
 * Code-only, not tokens: tokens.json has no size group. The icon sizes answer M1-M1a review #9; the
 * rest are component dimensions from the handoff's trip page (its px at the 390px viewport). They are
 * named here so feature code holds no `.dp` (ModuleGraphTest) and a second screen reuses them, and
 * they do not vary by theme. See docs/design-language.md → "Code-only sizes".
 */
object BahrSize {
    /** Icons inside a 26px marker, the seat icon on a date card, the check/close on included rows. */
    val iconSmall = 16.dp

    /** Tip and eyebrow icons. */
    val iconMedium = 18.dp

    /** The facts grid's icons. */
    val iconLarge = 22.dp

    /** Itinerary dots and the circles in front of included/excluded rows. */
    val marker = 26.dp

    /** Reviewers' initial circles. */
    val avatarSmall = 32.dp

    /** The host card's photo. */
    val avatar = 52.dp

    /** The itinerary's time column, wide enough for "≈12:15". */
    val timeColumn = 52.dp

    /** The vertical line joining itinerary dots. */
    val connector = 2.dp

    /** One date card in the availability row. */
    val dateCard = 100.dp

    /** A skeleton line while text loads. */
    val skeletonLine = 12.dp

    /** The custom radio on a date row of the date + party screen (HANDOFF screen 4). */
    val radio = 22.dp

    /** One segment of the booking flow's three-step indicator (24x4px in the handoff). */
    val stepSegmentWidth = 24.dp
    val stepSegmentHeight = 4.dp

    /** The party stepper's count, wide enough that 1 → 6 does not move the buttons. */
    val stepperValue = 30.dp
}

internal val LocalBahrShapes = staticCompositionLocalOf { BahrShapes() }
