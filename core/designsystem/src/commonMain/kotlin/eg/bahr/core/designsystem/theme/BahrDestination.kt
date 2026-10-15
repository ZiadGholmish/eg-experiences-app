package eg.bahr.core.designsystem.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/** Whether a back swipe drives the pop under way; set by [BahrSharedTransitions]. */
internal val LocalBackSwipe = staticCompositionLocalOf<() -> BahrBackSwipe> { { BahrBackSwipe.None } }

/**
 * Every NavHost destination's body (M3-M12): the screen on an opaque page in the theme's background,
 * so while one screen slides over another (BahrScreenTransitions.kt) neither shows through, whatever the
 * screen itself paints. [scope] is the destination's enter/exit, handed down for the shared elements
 * that follow it ([bahrSharedBounds]).
 *
 * Here rather than in each screen, so the screens' own goldens stay as they are and no destination can
 * be left see-through.
 */
@Composable
fun BahrDestination(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) = BahrDestination(scope, platformScreenMotionStyle, content)

/**
 * [BahrDestination] in a given [style]. On iOS the page also draws the dim over the screen it slides
 * across: everything beside its own bounds (where that screen shows), at [BahrMotion.ScreenUnderlayDim]
 * while the page is fully in, fading with the page as it comes in or leaves. Drawn by the page on top
 * rather than by the one underneath because only the page knows it is the one coming or going, and its
 * dim then runs on the very transition that slides it, swipe included. A page at rest draws it off
 * screen, and the page underneath draws its own beside it under the page on top: neither shows.
 */
@Composable
internal fun BahrDestination(
    scope: AnimatedVisibilityScope,
    style: ScreenMotionStyle,
    content: @Composable () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    // Under reduce motion the screens change at once: no dim, and no animation that would hold the
    // transition open for its length.
    val dim = if (style == ScreenMotionStyle.Ios && !BahrTheme.reducedMotion) scope.underlayDim(style) else null
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (dim == null) {
                    Modifier
                } else {
                    Modifier
                        .drawBehind {
                            // Read here, in the draw phase: the dim changes every frame without recomposing anything.
                            val alpha = dim.value
                            if (alpha > 0f) {
                                drawRect(
                                    Color.Black.copy(alpha = alpha),
                                    topLeft = Offset(-size.width, 0f),
                                    size = Size(size.width * OUTSIDE_WIDTHS, size.height),
                                )
                            }
                        }
                        // The page in a layer of its own: a new dim each frame redraws that one rectangle,
                        // not the whole screen under it.
                        .graphicsLayer()
                },
            ).background(background),
    ) {
        CompositionLocalProvider(LocalNavVisibilityScope provides scope, content = content)
    }
}

/**
 * The dim this page casts beside it: full while it is in, none before it enters or after it leaves, on
 * the same curve as its slide (linear while a back swipe drives it out).
 */
@Composable
private fun AnimatedVisibilityScope.underlayDim(style: ScreenMotionStyle): State<Float> {
    val backSwipe = LocalBackSwipe.current
    return transition.animateFloat(
        transitionSpec = {
            val leaving = targetState == EnterExitState.PostExit
            style.motion(swiping = leaving && backSwipe() != BahrBackSwipe.None).spec()
        },
        label = "underlayDim",
    ) { state -> if (state == EnterExitState.Visible) BahrMotion.ScreenUnderlayDim else 0f }
}

/** The dim covers one page width on each side of the page and the page itself (which then paints over it). */
private const val OUTSIDE_WIDTHS = 3f
