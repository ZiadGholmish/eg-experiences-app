package com.egyptexperiences.core.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Corner radii from the canvas. The design leans hard on two of them: [pill]
 * for every chip, badge and primary button, and [card] for photo tiles and
 * content blocks.
 */
@Immutable
class AppShapes(
    val none: CornerBasedShape = RoundedCornerShape(0.dp),
    val xs: CornerBasedShape = RoundedCornerShape(6.dp),
    val sm: CornerBasedShape = RoundedCornerShape(12.dp),
    val md: CornerBasedShape = RoundedCornerShape(14.dp),
    val lg: CornerBasedShape = RoundedCornerShape(16.dp),
    val card: CornerBasedShape = RoundedCornerShape(20.dp),
    val xl: CornerBasedShape = RoundedCornerShape(24.dp),
    /** Bottom sheets and the trip-detail content slab that overlaps the hero. */
    val sheet: CornerBasedShape =
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
    val pill: CornerBasedShape = RoundedCornerShape(100.dp),
)

internal val LocalShapes = staticCompositionLocalOf { AppShapes() }
