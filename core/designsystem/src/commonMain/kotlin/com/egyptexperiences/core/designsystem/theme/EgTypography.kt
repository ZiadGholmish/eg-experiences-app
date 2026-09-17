package com.egyptexperiences.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Named styles from the canvas, for the cases the Material slots do not cover.
 *
 * The canvas names hierarchy by role, not by size — a price is a price whether
 * it sits on a card at 20sp or in the checkout summary at 30sp.
 */
@Immutable
data class EgTypography(
    /** Screen title on the hero: 26/ExtraBold. */
    val screenTitle: TextStyle,
    /** Trip title on a card: 17/ExtraBold. */
    val cardTitle: TextStyle,
    /** The price, always teal, always ExtraBold. */
    val price: TextStyle,
    /** The large price in checkout and the host dashboard takings figure. */
    val priceLarge: TextStyle,
    /** Kicker above a title — 11.5/Medium, subdued. */
    val kicker: TextStyle,
    /** Chip and filter label — 11.5/Bold. */
    val chip: TextStyle,
    /** Badge text: "2 seats left", "Sold out" — 10.5/SemiBold, uppercase-ish. */
    val badge: TextStyle,
    /** Body copy in trip detail sections. */
    val body: TextStyle,
    /** Metadata under a title: duration, departure point. */
    val meta: TextStyle,
    /** Primary button label — 14/Bold. */
    val button: TextStyle,
)

@Composable
internal fun egTypography(): EgTypography {
    val family = EgFontFamilies.manrope

    fun style(
        size: Double,
        weight: FontWeight,
        lineHeight: Double,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
    )
    return EgTypography(
        screenTitle = style(26.0, FontWeight.ExtraBold, 32.0),
        cardTitle = style(17.0, FontWeight.ExtraBold, 22.0),
        price = style(20.0, FontWeight.ExtraBold, 24.0),
        priceLarge = style(30.0, FontWeight.ExtraBold, 36.0),
        kicker = style(11.5, FontWeight.Medium, 16.0),
        chip = style(11.5, FontWeight.Bold, 16.0),
        badge = style(10.5, FontWeight.SemiBold, 14.0),
        body = style(13.5, FontWeight.Medium, 20.0),
        meta = style(12.0, FontWeight.SemiBold, 16.0),
        button = style(14.0, FontWeight.Bold, 18.0),
    )
}

internal val LocalEgTypography =
    staticCompositionLocalOf<EgTypography> {
        error("EgTypography is only available inside EgTheme")
    }
