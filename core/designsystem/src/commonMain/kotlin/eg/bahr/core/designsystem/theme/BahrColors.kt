package eg.bahr.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Ported from the design handoff, vendored at `../docs/design/compose-reference/theme/BahrColors.kt`
 * (relative to mobile-app). Source of truth: `../docs/design/design-system/tokens.json`
 * (`ref.palette`, `sys.color`), plus `tokens.css` next to it for the two alternate themes, which
 * tokens.json only names. If a value changes, change it there first, then here.
 */

/**
 * Reference palette — hues sampled from Lake Burullus.
 * Mirrors `ref.palette` in tokens.json. Screens never use these directly.
 */
internal object BahrPalette {
    val Teal30 = Color(0xFF0A5B63)
    val Teal40 = Color(0xFF0E7C86)
    val Teal50 = Color(0xFF12A09A)
    val Teal60 = Color(0xFF2AB7C9)
    val Teal80 = Color(0xFF9FE3E8)
    val Teal95 = Color(0xFFEAF7F8)
    val Coral30 = Color(0xFFA93C1C)
    val Coral40 = Color(0xFFD94F28)
    val Coral50 = Color(0xFFF2562F)
    val Coral60 = Color(0xFFFF6B4A)
    val Coral95 = Color(0xFFFFF0E9)
    val Gold30 = Color(0xFF7A5A0E)
    val Gold40 = Color(0xFFC98A0E)
    val Gold60 = Color(0xFFF2B33D)
    val Gold95 = Color(0xFFFFF6E3)
    val Magenta30 = Color(0xFF8E2A62)
    val Magenta50 = Color(0xFFC2478F)
    val Magenta95 = Color(0xFFFBEAF4)
    val Green30 = Color(0xFF1A6B4E)
    val Green40 = Color(0xFF1F8A63)
    val Green50 = Color(0xFF2F8F7F)
    val Green95 = Color(0xFFE5F6EF)
    val Neutral10 = Color(0xFF12232A)
    val Neutral30 = Color(0xFF41585F)
    val Neutral50 = Color(0xFF5C6F77)
    val Neutral70 = Color(0xFF8B9B9F)
    val Neutral85 = Color(0xFFC9D6D8)
    val Neutral90 = Color(0xFFDDE7E8)
    val Neutral94 = Color(0xFFF2F6F6)
    val Neutral97 = Color(0xFFF7FAFA)
    val Neutral99 = Color(0xFFF9FBFB)
    val Neutral100 = Color(0xFFFFFFFF)

    /** `rgba(9,22,26,…)` — the base of every scrim over photography. */
    val Ink = Color(0xFF09161A)
}

/**
 * Roles Material 3's ColorScheme has no slot for. Read via `BahrTheme.colors.x`.
 * Coral (`tertiary` in the M3 scheme) is the action colour and nothing else.
 */
@Immutable
data class BahrExtendedColors(
    val primaryDim: Color,
    val primaryBright: Color,
    val primaryDeep: Color,
    val secondaryDim: Color,
    val tertiaryHover: Color,
    val quaternary: Color,
    val quaternaryContainer: Color,
    val onQuaternaryContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surfaceDim: Color,
    val surfaceTranslucent: Color,
    val onSurfaceSecondary: Color,
    val onSurfaceDisabled: Color,
    val track: Color,
    val scrim: Color,
    /** Base of the top/bottom image gradients. */
    val scrimInk: Color,
    /** `elevation.cta` — coral-tinted, so the CTA reads as lifted off the page. */
    val ctaShadow: Color,
    /** `elevation.chip` — teal-tinted, under a selected filter chip. */
    val chipShadow: Color,
    /**
     * Ink of the untinted shadows (`level1..3`, `sticky`). Each level applies its own alpha from
     * tokens.json, so this carries the hue only.
     */
    val shadowInk: Color,
    /** HighContrast replaces `level1..3` shadows with a 1dp outline (`tokens.css`). */
    val outlinedElevation: Boolean,
)

internal data class BahrColorTheme(
    val scheme: ColorScheme,
    val extended: BahrExtendedColors,
)

private val P = BahrPalette

private const val SURFACE_TRANSLUCENT_ALPHA = .94f
private const val SCRIM_ALPHA = .5f
private const val CTA_SHADOW_ALPHA = .34f
private const val CHIP_SHADOW_ALPHA = .26f
private const val DUSK_CTA_SHADOW_ALPHA = .3f

internal val LakeBurullus =
    BahrColorTheme(
        scheme =
            lightColorScheme(
                primary = P.Teal40,
                onPrimary = P.Neutral100,
                primaryContainer = P.Teal95,
                onPrimaryContainer = P.Teal30,
                secondary = P.Gold60,
                onSecondary = Color(0xFF3B2C06),
                secondaryContainer = P.Gold95,
                onSecondaryContainer = P.Gold30,
                tertiary = P.Coral60,
                onTertiary = P.Neutral100,
                tertiaryContainer = P.Coral95,
                onTertiaryContainer = P.Coral30,
                error = P.Coral40,
                onError = P.Neutral100,
                errorContainer = P.Coral95,
                onErrorContainer = P.Coral30,
                background = P.Neutral100,
                onBackground = P.Neutral10,
                surface = P.Neutral100,
                onSurface = P.Neutral10,
                surfaceVariant = P.Neutral94,
                onSurfaceVariant = P.Neutral50,
                surfaceContainerLowest = P.Neutral99,
                surfaceContainerLow = P.Neutral97,
                surfaceContainer = P.Neutral94,
                surfaceDim = Color(0xFFDFE9EA),
                outline = P.Neutral85,
                outlineVariant = P.Neutral90,
                scrim = P.Ink,
            ),
        extended =
            BahrExtendedColors(
                primaryDim = Color(0xFF4A6E73),
                primaryBright = P.Teal80,
                primaryDeep = Color(0xFF062126),
                secondaryDim = P.Gold40,
                tertiaryHover = P.Coral50,
                quaternary = P.Magenta50,
                quaternaryContainer = P.Magenta95,
                onQuaternaryContainer = P.Magenta30,
                success = P.Green50,
                successContainer = P.Green95,
                onSuccessContainer = P.Green30,
                surfaceLowest = P.Neutral99,
                surfaceLow = P.Neutral97,
                surfaceDim = Color(0xFFDFE9EA),
                surfaceTranslucent = P.Neutral100.copy(alpha = SURFACE_TRANSLUCENT_ALPHA),
                onSurfaceSecondary = P.Neutral30,
                onSurfaceDisabled = P.Neutral70,
                track = Color(0xFFD9E5E6),
                scrim = P.Ink.copy(alpha = SCRIM_ALPHA),
                scrimInk = P.Ink,
                ctaShadow = P.Coral60.copy(alpha = CTA_SHADOW_ALPHA),
                chipShadow = P.Teal40.copy(alpha = CHIP_SHADOW_ALPHA),
                shadowInk = P.Neutral10,
                outlinedElevation = false,
            ),
    )

/** Alternate theme: cooler navy primary, brick action colour. Matches `[data-theme="dusk"]`. */
internal val Dusk =
    LakeBurullus.let { base ->
        BahrColorTheme(
            scheme =
                base.scheme.copy(
                    primary = Color(0xFF1B4A6B),
                    primaryContainer = Color(0xFFE7EFF5),
                    onPrimaryContainer = Color(0xFF12324A),
                    tertiary = Color(0xFFC8552F),
                    tertiaryContainer = Color(0xFFFBEBE4),
                    secondary = Color(0xFFD99B3F),
                    surfaceContainerLow = Color(0xFFF8F7F5),
                    surfaceContainer = Color(0xFFF1EFEC),
                    onSurface = Color(0xFF1C1F22),
                ),
            extended =
                base.extended.copy(
                    primaryDim = Color(0xFF4D6A80),
                    primaryBright = Color(0xFFA7CBE3),
                    tertiaryHover = Color(0xFFAD4523),
                    surfaceLow = Color(0xFFF8F7F5),
                    ctaShadow = Color(0xFFC8552F).copy(alpha = DUSK_CTA_SHADOW_ALPHA),
                    chipShadow = Color(0xFF1B4A6B).copy(alpha = CHIP_SHADOW_ALPHA),
                ),
        )
    }

/**
 * Alternate theme: darker ink; cards use a 1dp outline instead of shadow.
 * Matches `[data-theme="highcontrast"]`.
 */
internal val HighContrast =
    LakeBurullus.let { base ->
        BahrColorTheme(
            scheme =
                base.scheme.copy(
                    primary = Color(0xFF045A63),
                    onSurfaceVariant = Color(0xFF3B4C52),
                    outline = Color(0xFF7F9296),
                    outlineVariant = Color(0xFFB4C4C6),
                    tertiary = Color(0xFFD6421C),
                    onTertiaryContainer = Color(0xFF7C2A13),
                ),
            extended =
                base.extended.copy(
                    onSurfaceSecondary = Color(0xFF1D2F35),
                    outlinedElevation = true,
                ),
        )
    }

enum class BahrThemeName { LakeBurullus, Dusk, HighContrast }

internal fun bahrColorTheme(name: BahrThemeName) =
    when (name) {
        BahrThemeName.LakeBurullus -> LakeBurullus
        BahrThemeName.Dusk -> Dusk
        BahrThemeName.HighContrast -> HighContrast
    }

internal val LocalBahrColors = staticCompositionLocalOf { LakeBurullus.extended }
