package com.egyptexperiences.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing steps the canvas actually uses. Reached through
 * `EgTheme.spacings`, never as a literal `.dp` in a feature module.
 */
@Immutable
data class AppSpacings(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 6.dp,
    val md: Dp = 8.dp,
    val lg: Dp = 10.dp,
    val xl: Dp = 12.dp,
    val xxl: Dp = 14.dp,
    val x3l: Dp = 16.dp,
    /** The canvas gutter: every 390px artboard pads its content by 18. */
    val screenGutter: Dp = 18.dp,
    val x4l: Dp = 20.dp,
    val x5l: Dp = 24.dp,
    val x6l: Dp = 28.dp,
    val x7l: Dp = 32.dp,
    val x8l: Dp = 40.dp,
    val x9l: Dp = 48.dp,
)

internal val LocalSpacings = staticCompositionLocalOf { AppSpacings() }
