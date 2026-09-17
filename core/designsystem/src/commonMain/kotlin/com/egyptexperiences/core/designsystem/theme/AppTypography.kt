package com.egyptexperiences.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.egyptexperiences.core.designsystem.generated.resources.Res
import com.egyptexperiences.core.designsystem.generated.resources.ibm_plex_sans_arabic_bold
import com.egyptexperiences.core.designsystem.generated.resources.ibm_plex_sans_arabic_medium
import com.egyptexperiences.core.designsystem.generated.resources.ibm_plex_sans_arabic_regular
import com.egyptexperiences.core.designsystem.generated.resources.ibm_plex_sans_arabic_semibold
import com.egyptexperiences.core.designsystem.generated.resources.manrope_bold
import com.egyptexperiences.core.designsystem.generated.resources.manrope_extrabold
import com.egyptexperiences.core.designsystem.generated.resources.manrope_medium
import com.egyptexperiences.core.designsystem.generated.resources.manrope_regular
import com.egyptexperiences.core.designsystem.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

object EgFontFamilies {
    /**
     * Manrope serves Latin; IBM Plex Sans Arabic is registered in the same
     * [FontFamily] as the fallback for Arabic codepoints, so one [TextStyle]
     * renders both scripts correctly without the caller choosing a family.
     *
     * This matters more here than in most apps: `ar` is the default locale, so
     * the Arabic cut is the common case, not the exception. Static weight cuts
     * rather than the variable Manrope because minSdk is 24 and Android only
     * honours variable-font weight axes from API 26.
     */
    val manrope: FontFamily
        @Composable get() =
            FontFamily(
                Font(Res.font.manrope_regular, FontWeight.Normal),
                Font(Res.font.manrope_medium, FontWeight.Medium),
                Font(Res.font.manrope_semibold, FontWeight.SemiBold),
                Font(Res.font.manrope_bold, FontWeight.Bold),
                Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
                Font(Res.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
                Font(Res.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
                Font(Res.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
                Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
                Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.ExtraBold),
            )
}

/**
 * The canvas type scale.
 *
 * Sizes are lifted from the artboards' `font:` shorthands, which cluster far
 * tighter than a Material scale does — the design runs 10.5sp to 30sp with
 * weight, not size, carrying most of the hierarchy. Material's slots are mapped
 * to the nearest canvas step so `MaterialTheme.typography.*` stays usable, but
 * new code should prefer [EgTypography] via `EgTheme.typography`.
 */
@Composable
fun egMaterialTypography(): Typography {
    val family = EgFontFamilies.manrope

    fun style(
        size: Int,
        weight: FontWeight,
        lineHeight: Int,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
    )
    return Typography(
        displayLarge = style(30, FontWeight.ExtraBold, 36),
        displayMedium = style(26, FontWeight.ExtraBold, 32),
        displaySmall = style(22, FontWeight.ExtraBold, 28),
        headlineLarge = style(22, FontWeight.ExtraBold, 28),
        headlineMedium = style(20, FontWeight.ExtraBold, 26),
        headlineSmall = style(18, FontWeight.ExtraBold, 24),
        titleLarge = style(17, FontWeight.ExtraBold, 22),
        titleMedium = style(15, FontWeight.Bold, 20),
        titleSmall = style(14, FontWeight.Bold, 18),
        bodyLarge = style(14, FontWeight.Medium, 20),
        bodyMedium = style(13, FontWeight.Medium, 18),
        bodySmall = style(12, FontWeight.Medium, 16),
        labelLarge = style(13, FontWeight.Bold, 16),
        labelMedium = style(12, FontWeight.Bold, 15),
        labelSmall = style(11, FontWeight.SemiBold, 14),
    )
}
