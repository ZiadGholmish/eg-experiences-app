package com.egyptexperiences.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Palette harvested from the design canvas — the 8 mobile artboards cited in
// `../be/docs/plan/implementation-plan.md`.
//
// Provisional until the claude.ai/design design system is synced — see
// `docs/design-language.md`. Anything a sync contradicts, the sync wins.
//
// Note for anyone re-deriving these: the canvas bundle embeds a map widget
// whose own chrome uses a different teal (`#14606A`) and Roboto. That is
// Leaflet CSS, not this product's design. It is not in this file on purpose.

// Brand — teal carries every primary action, header and price.
val EgTeal = Color(0xFF0E7C86)
val EgTealTint = Color(0xFFEAF7F8)
val EgTealDeep = Color(0xFF12232A)

// Accent — coral marks urgency: the hold timer, "2 seats left", sold-out.
val EgCoral = Color(0xFFFF6B4A)
val EgCoralTint = Color(0xFFFFF0E9)
val EgCoralDeep = Color(0xFFA93C1C)

// Text
val EgInk = Color(0xFF12232A)
val EgInkMuted = Color(0xFF41585F)
val EgInkSubtle = Color(0xFF5C6F77)
val EgInkFaint = Color(0xFF8B9B9F)

// Surfaces
val EgSurface = Color(0xFFFFFFFF)
val EgSurfaceMuted = Color(0xFFF7FAFA)
val EgSurfaceSunken = Color(0xFFF2F6F6)

// Strokes
val EgStroke = Color(0xFFDFE9EA)
val EgStrokeStrong = Color(0xFFC9D6D8)
val EgStrokeFaint = Color(0xFFE6EBEC)

// Status
val EgSuccess = Color(0xFF1A6B4E)
val EgSuccessTint = Color(0xFFE5F6EF)
val EgWarning = Color(0xFFC98A0E)
val EgWarningAccent = Color(0xFFF2B33D)
val EgWarningTint = Color(0xFFFFF6E3)
val EgDanger = Color(0xFFD94F28)
val EgDangerTint = Color(0xFFFFF0E9)

val EgOnBrand = Color(0xFFFFFFFF)
