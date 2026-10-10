package eg.bahr.core.designsystem.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/*
 * The screen transition for a page opened from Home: a category, a row's "See all", search (M4-M6).
 * Forward, the new page slides in from the end side while Home slides a little toward the start,
 * both fading; Back plays it in reverse. `SlideDirection.Start`/`End` follow the layout direction,
 * so in Arabic the page comes in from the left. Partial slides ([BahrMotion.ScreenSlideFraction]):
 * the move says "forward" or "back" without sweeping the whole page across.
 *
 * Navigation calls these outside composition, so they take reduce motion as a value: with it on,
 * the screens change at once.
 */

/** The opened page coming in. */
fun AnimatedContentTransitionScope<*>.bahrForwardEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) {
        EnterTransition.None
    } else {
        slideIntoContainer(SlideDirection.Start, screenTween(), initialOffset = ::partialSlide) + fadeIn(screenTween())
    }

/** The page underneath (Home) as the opened one comes in. */
fun AnimatedContentTransitionScope<*>.bahrForwardExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) {
        ExitTransition.None
    } else {
        slideOutOfContainer(SlideDirection.Start, screenTween(), targetOffset = ::partialSlide) + fadeOut(screenTween())
    }

/** The page underneath coming back on Back. */
fun AnimatedContentTransitionScope<*>.bahrBackEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) {
        EnterTransition.None
    } else {
        slideIntoContainer(SlideDirection.End, screenTween(), initialOffset = ::partialSlide) + fadeIn(screenTween())
    }

/** The opened page leaving on Back. */
fun AnimatedContentTransitionScope<*>.bahrBackExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) {
        ExitTransition.None
    } else {
        slideOutOfContainer(SlideDirection.End, screenTween(), targetOffset = ::partialSlide) + fadeOut(screenTween())
    }

private fun <T> screenTween() = tween<T>(BahrMotion.Medium, easing = BahrMotion.Standard)

private fun partialSlide(fullSlide: Int): Int = (fullSlide * BahrMotion.ScreenSlideFraction).toInt()
