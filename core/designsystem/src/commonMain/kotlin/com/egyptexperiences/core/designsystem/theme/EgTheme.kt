package com.egyptexperiences.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val LightColorScheme =
    lightColorScheme(
        primary = EgTeal,
        onPrimary = EgOnBrand,
        primaryContainer = EgTealTint,
        onPrimaryContainer = EgTeal,
        secondary = EgCoral,
        onSecondary = EgOnBrand,
        secondaryContainer = EgCoralTint,
        onSecondaryContainer = EgCoralDeep,
        background = EgSurface,
        onBackground = EgInk,
        surface = EgSurface,
        onSurface = EgInk,
        surfaceVariant = EgSurfaceMuted,
        onSurfaceVariant = EgInkSubtle,
        outline = EgStroke,
        outlineVariant = EgStrokeFaint,
        error = EgDanger,
        onError = EgOnBrand,
        errorContainer = EgDangerTint,
        onErrorContainer = EgCoralDeep,
    )

/**
 * Light only. The canvas has no dark artboards, and shipping a guessed dark
 * palette is worse than shipping none — see `docs/design-language.md`.
 */
@Composable
fun EgTheme(content: @Composable () -> Unit) {
    val materialTypography = egMaterialTypography()

    CompositionLocalProvider(
        LocalSpacings provides AppSpacings(),
        LocalShapes provides AppShapes(),
        LocalEgTypography provides egTypography(),
    ) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = materialTypography,
            content = content,
        )
    }
}

/** Accessors for the tokens Material's own theme object has no slot for. */
object EgTheme {
    val spacings: AppSpacings
        @Composable @ReadOnlyComposable
        get() = LocalSpacings.current

    val shapes: AppShapes
        @Composable @ReadOnlyComposable
        get() = LocalShapes.current

    val typography: EgTypography
        @Composable @ReadOnlyComposable
        get() = LocalEgTypography.current
}
