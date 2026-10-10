package eg.bahr.core.designsystem.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/*
 * Reduce motion (M4-M6): the platform's own setting, read once at the theme and handed down.
 *
 * - Android: "Remove animations" (Settings → Accessibility), which sets the animator duration scale
 *   to 0. Compose on Android already scales every animation's duration by it, so most motion is
 *   instant there anyway; the flag is still read so that what is not a duration (a slide's offset, a
 *   shared-element morph, a screen transition) is skipped too.
 * - iOS: Settings → Accessibility → Motion → Reduce Motion. Compose on iOS does not read it, so
 *   without this flag nothing would honour it.
 *
 * With it on, every animation the design system offers is instant: colours and counts change at
 * once, lists do not slide, screens change without a transition.
 */

/** Whether the platform asks for reduced motion, kept current while the screen is shown. */
@Composable
internal expect fun systemReducesMotion(): Boolean

internal val LocalBahrReducedMotion = staticCompositionLocalOf { false }

/** True when animations should be instant. See [BahrTheme] (`reducedMotion`). */
val BahrTheme.reducedMotion: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalBahrReducedMotion.current

/**
 * A token tween, or an instant change under reduce motion. Every animation in the app goes through
 * this (or [BahrScreenTransitions]) so no screen has to remember the setting itself.
 */
@Composable
@ReadOnlyComposable
fun <T> bahrTween(
    durationMillis: Int = BahrMotion.Medium,
    easing: Easing = BahrMotion.Standard,
): FiniteAnimationSpec<T> = if (BahrTheme.reducedMotion) snap() else tween(durationMillis, easing = easing)

/**
 * A token tween, or null under reduce motion: for APIs where null means "do not animate"
 * (`LazyItemScope.animateItem`'s fade and placement specs).
 */
@Composable
@ReadOnlyComposable
fun <T> bahrTweenOrNull(
    durationMillis: Int = BahrMotion.Medium,
    easing: Easing = BahrMotion.Standard,
): FiniteAnimationSpec<T>? = if (BahrTheme.reducedMotion) null else tween(durationMillis, easing = easing)
