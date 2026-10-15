package eg.bahr.core.designsystem.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/*
 * Screen transitions (M3-M12): how every push and pop in a tab's NavHost moves, one set for the whole
 * app, in each platform's own style. The app's NavHost takes them; no destination sets its own.
 *
 * - iOS (UINavigationController): the opened screen slides in from the trailing edge over the whole
 *   width; the one underneath shifts [BahrMotion.ScreenUnderlayShift] toward the start and dims to
 *   [BahrMotion.ScreenUnderlayDim] (drawn by the screen on top, [BahrDestination]). Back plays it in reverse, and the edge swipe drags it with the finger.
 * - Android (Android 14's activity transition): the opened screen slides a short way in from the end
 *   and fades in quickly; the one underneath slides the same short way toward the start. Back mirrors
 *   it, and a predictive back gesture scrubs it.
 *
 * Every screen is opaque ([BahrDestination]) and only the screen on top ever fades, so the window behind
 * never shows through. Slides are placement, fades graphics-layer and the dim draw-phase animations: a
 * transition recomposes nothing per frame.
 *
 * Directions are `Start`/`End`, which follow the layout direction, so in Arabic everything mirrors: a
 * screen comes in from the left and Back sends it out to the left. A back swipe ([BahrBackSwipe]) is the
 * exception: the page follows the finger, from whichever edge it started.
 *
 * Navigation calls these outside composition, so they take reduce motion as a value: with it on, the
 * screens change at once.
 */

/** Which platform's navigation motion a transition follows. Internal: the screenshot tests draw both on the JVM. */
internal enum class ScreenMotionStyle { Ios, Android }

/** This platform's [ScreenMotionStyle]. */
internal expect val platformScreenMotionStyle: ScreenMotionStyle

/**
 * Whether a pop is driven by a back swipe in progress (iOS's edge swipe, Android's predictive back),
 * and from which edge. A swiped pop runs on a linear curve, so the page stays under the finger (the
 * gesture's progress is the finger's share of the width); on iOS it also moves toward the side the finger
 * is moving to.
 */
enum class BahrBackSwipe { None, FromLeftEdge, FromRightEdge }

/** The opened screen coming in. */
fun AnimatedContentTransitionScope<*>.bahrPushEnter(reducedMotion: Boolean): EnterTransition =
    pushEnter(reducedMotion, platformScreenMotionStyle)

/** The screen underneath as the opened one comes in. */
fun AnimatedContentTransitionScope<*>.bahrPushExit(reducedMotion: Boolean): ExitTransition =
    pushExit(reducedMotion, platformScreenMotionStyle)

/** The screen underneath coming back on Back. */
fun AnimatedContentTransitionScope<*>.bahrPopEnter(
    reducedMotion: Boolean,
    swipe: BahrBackSwipe = BahrBackSwipe.None,
): EnterTransition = popEnter(reducedMotion, swipe, platformScreenMotionStyle)

/** The screen on top leaving on Back. */
fun AnimatedContentTransitionScope<*>.bahrPopExit(
    reducedMotion: Boolean,
    swipe: BahrBackSwipe = BahrBackSwipe.None,
): ExitTransition = popExit(reducedMotion, swipe, platformScreenMotionStyle)

/**
 * A screen that replaces the one on screen rather than going over it (Home after the launch screen): it
 * fades in on top, and [bahrReplaceExit] keeps the old one fully drawn under it until it has.
 */
fun bahrReplaceEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) EnterTransition.None else fadeIn(tween(BahrMotion.Medium, easing = BahrMotion.Standard))

/** The screen being replaced: it stays, opaque, until [bahrReplaceEnter] has covered it. */
fun bahrReplaceExit(reducedMotion: Boolean): ExitTransition =
    // A "fade" to full opacity: the public way to keep an exiting screen drawn as it is for a while.
    if (reducedMotion) ExitTransition.None else fadeOut(tween(BahrMotion.Medium), targetAlpha = 1f)

internal fun AnimatedContentTransitionScope<*>.pushEnter(
    reducedMotion: Boolean,
    style: ScreenMotionStyle,
): EnterTransition {
    if (reducedMotion) return EnterTransition.None
    val motion = style.motion(swiping = false)
    return when (style) {
        ScreenMotionStyle.Ios -> slideIntoContainer(SlideDirection.Start, motion.spec())
        ScreenMotionStyle.Android ->
            slideIntoContainer(SlideDirection.Start, motion.spec(), initialOffset = ::androidSlide) +
                fadeIn(quickFade())
    }
}

internal fun AnimatedContentTransitionScope<*>.pushExit(
    reducedMotion: Boolean,
    style: ScreenMotionStyle,
): ExitTransition {
    if (reducedMotion) return ExitTransition.None
    val motion = style.motion(swiping = false)
    return when (style) {
        // The dim is the opened screen's to draw (BahrDestination), so it moves exactly with the slide.
        ScreenMotionStyle.Ios -> slideOutOfContainer(SlideDirection.Start, motion.spec(), targetOffset = ::underlayShift)
        ScreenMotionStyle.Android -> slideOutOfContainer(SlideDirection.Start, motion.spec(), targetOffset = ::androidSlide)
    }
}

internal fun AnimatedContentTransitionScope<*>.popEnter(
    reducedMotion: Boolean,
    swipe: BahrBackSwipe,
    style: ScreenMotionStyle,
): EnterTransition {
    if (reducedMotion) return EnterTransition.None
    val motion = style.motion(swiping = swipe != BahrBackSwipe.None)
    return when (style) {
        ScreenMotionStyle.Ios -> slideIntoContainer(swipe.towards(), motion.spec(), initialOffset = ::underlayShift)
        ScreenMotionStyle.Android -> slideIntoContainer(SlideDirection.End, motion.spec(), initialOffset = ::androidSlide)
    }
}

internal fun AnimatedContentTransitionScope<*>.popExit(
    reducedMotion: Boolean,
    swipe: BahrBackSwipe,
    style: ScreenMotionStyle,
): ExitTransition {
    if (reducedMotion) return ExitTransition.None
    val swiping = swipe != BahrBackSwipe.None
    val motion = style.motion(swiping)
    return when (style) {
        ScreenMotionStyle.Ios -> slideOutOfContainer(swipe.towards(), motion.spec())
        // Swiped, the page fades over the whole gesture, so it is still there under the finger midway.
        ScreenMotionStyle.Android ->
            slideOutOfContainer(SlideDirection.End, motion.spec(), targetOffset = ::androidSlide) +
                fadeOut(if (swiping) motion.spec() else quickFade())
    }
}

/** A transition's duration and curve. */
internal class ScreenMotion(
    val durationMillis: Int,
    val easing: Easing,
) {
    fun <T> spec(): FiniteAnimationSpec<T> = tween(durationMillis, easing = easing)
}

/** A swiped pop is linear, so the page tracks the finger; everything else uses the platform's curve. */
internal fun ScreenMotionStyle.motion(swiping: Boolean): ScreenMotion =
    when (this) {
        ScreenMotionStyle.Ios -> ScreenMotion(BahrMotion.ScreenIos, if (swiping) LinearEasing else BahrMotion.IosNavigation)
        ScreenMotionStyle.Android -> ScreenMotion(BahrMotion.ScreenAndroid, if (swiping) LinearEasing else BahrMotion.Emphasized)
    }

/**
 * Where an iOS pop sends the screen on top: toward the end, or with a swipe, the way the finger moves
 * (away from the edge it started at).
 */
private fun BahrBackSwipe.towards(): SlideDirection =
    when (this) {
        BahrBackSwipe.None -> SlideDirection.End
        BahrBackSwipe.FromLeftEdge -> SlideDirection.Right
        BahrBackSwipe.FromRightEdge -> SlideDirection.Left
    }

/** The duration and curve [bahrSharedBounds] morphs on, so a shared element lands with the screen around it. */
internal fun <T> screenTransitionSpec(): FiniteAnimationSpec<T> = platformScreenMotionStyle.motion(swiping = false).spec()

/** The Android screen's short fade: Android 14's is under a tenth of a second; [BahrMotion.Short] is the nearest token. */
private fun <T> quickFade(): FiniteAnimationSpec<T> = tween(BahrMotion.Short, easing = LinearEasing)

private fun underlayShift(fullSlide: Int): Int = (fullSlide * BahrMotion.ScreenUnderlayShift).toInt()

private fun androidSlide(fullSlide: Int): Int = (fullSlide * BahrMotion.ScreenSlideFraction).toInt()
