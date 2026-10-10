package eg.bahr.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Mirrors `sys.typescale` in tokens.json. Sizes, weights and tracking come from there; line
 * heights tokens.json leaves open come from the handoff port. Font files: see BahrFonts.kt.
 */

/** Product-specific styles beyond the M3 Typography slots. */
@Immutable
data class BahrExtendedType(
    /** 40 / 800 — the booking reference. */
    val display: TextStyle,
    /** 10.5 / 600 / +0.1em, uppercase in English only (see [overlineCase]). */
    val overline: TextStyle,
    /** 17 / 800 — prices, with tabular figures. */
    val price: TextStyle,
    /** 19 / 800 / -0.02em — the "bahr" wordmark in Home's app bar (HANDOFF Home, M4-M1). */
    val wordmark: TextStyle,
)

private const val MAX_FONT_WEIGHT = 900
private const val WEIGHT_STEP = 100

/** Arabic leading is looser than Latin by this much (README: body 1.65 → 1.8 in Arabic). */
private const val ARABIC_EXTRA_LEADING = 0.15f

/**
 * Arabic rules, from the design: one weight heavier at display/headline sizes,
 * looser leading, no letter-spacing, no uppercase.
 */
private fun FontWeight.heavier(): FontWeight = FontWeight((weight + WEIGHT_STEP).coerceAtMost(MAX_FONT_WEIGHT))

@Suppress("LongParameterList")
private fun style(
    family: FontFamily,
    size: Float,
    weight: FontWeight,
    lineHeight: Float,
    tracking: Float = 0f,
    arabic: Boolean,
    bumpWeight: Boolean = false,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = if (arabic && bumpWeight) weight.heavier() else weight,
    lineHeight = (size * if (arabic) lineHeight + ARABIC_EXTRA_LEADING else lineHeight).sp,
    letterSpacing = if (arabic) 0.em else tracking.em,
    // Tabular numerals everywhere: prices, times and seat counts line up in columns.
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private const val TRACKING_TIGHT = -.02f
private const val TRACKING_OVERLINE = .1f

internal fun bahrTypography(
    family: FontFamily,
    arabic: Boolean,
) = Typography(
    // typescale.display
    displaySmall = style(family, 40f, FontWeight.ExtraBold, 1.05f, TRACKING_TIGHT, arabic, bumpWeight = true),
    // typescale.headline
    headlineMedium = style(family, 26f, FontWeight.ExtraBold, 1.15f, TRACKING_TIGHT, arabic, bumpWeight = true),
    // typescale.titleLarge (no tracking in tokens.json)
    titleLarge = style(family, 21f, FontWeight.Bold, 1.2f, arabic = arabic, bumpWeight = true),
    // typescale.title (no tracking in tokens.json)
    titleMedium = style(family, 17f, FontWeight.Bold, 1.3f, arabic = arabic),
    // typescale.body
    bodyLarge = style(family, 14f, FontWeight.Normal, 1.65f, arabic = arabic),
    // typescale.bodySmall
    bodyMedium = style(family, 13f, FontWeight.Normal, 1.6f, arabic = arabic),
    // typescale.label
    labelLarge = style(family, 12f, FontWeight.Bold, 1.3f, arabic = arabic),
    // typescale.labelSmall
    labelMedium = style(family, 11f, FontWeight.SemiBold, 1.3f, arabic = arabic),
    // Not a token: Material's text fields animate their label between bodyLarge and bodySmall. Left at
    // Material's default, bodySmall's letter-spacing is in sp while ours is in em, and that animation
    // throws ("Cannot perform operation for Em and Sp") the moment a labelled field gets text or
    // focus. Sized as Material's floating label (12sp), on the Bahr family and rules.
    bodySmall = style(family, 12f, FontWeight.Normal, 1.4f, arabic = arabic),
)

internal fun bahrExtendedType(
    family: FontFamily,
    arabic: Boolean,
) = BahrExtendedType(
    display = style(family, 40f, FontWeight.ExtraBold, 1.05f, TRACKING_TIGHT, arabic, bumpWeight = true),
    overline = style(family, 10.5f, FontWeight.SemiBold, 1.3f, TRACKING_OVERLINE, arabic),
    price = style(family, 17f, FontWeight.ExtraBold, 1.1f, arabic = arabic),
    wordmark = style(family, 19f, FontWeight.ExtraBold, 1.1f, TRACKING_TIGHT, arabic),
)

/** Use instead of `.uppercase()` so Arabic stays caseless. */
fun String.overlineCase(arabic: Boolean) = if (arabic) this else uppercase()

internal val LocalBahrType = staticCompositionLocalOf<BahrExtendedType> { error("BahrTheme not set") }
