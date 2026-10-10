package eg.bahr.core.designsystem.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/*
 * Shared-element morphs between screens (M4-M6): a Home category chip grows into the category page's
 * header, a Home row's title into its "See all" page's heading. Compose's shared transitions are in
 * `compose.animation` common code, so the same morph runs on Android and iOS.
 *
 * The app wraps its NavHost in [BahrSharedTransitions]; each destination hands its own
 * AnimatedVisibilityScope down with [ProvideBahrNavScope]. Both scopes travel as CompositionLocals so
 * a screen deep in a feature can mark an element with [bahrSharedBounds] without its callers knowing.
 * Outside them (tests, previews) the modifier does nothing.
 */

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

private val LocalNavVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Around the NavHost: every destination under it can share elements with the others. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BahrSharedTransitions(content: @Composable () -> Unit) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) { content() }
    }
}

/** Inside one destination: [scope] is the destination's enter/exit, which a shared element follows. */
@Composable
fun ProvideBahrNavScope(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNavVisibilityScope provides scope, content = content)
}

/**
 * Marks this element as the same one as the element with [key] on the screen being left or opened:
 * during the screen transition its bounds morph from one to the other while the two contents
 * crossfade. Nothing under reduce motion, or with no [key], or outside a NavHost destination.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.bahrSharedBounds(key: String?): Modifier {
    val transitions = LocalSharedTransitionScope.current
    val destination = LocalNavVisibilityScope.current
    if (key == null || transitions == null || destination == null || BahrTheme.reducedMotion) return this
    return with(transitions) {
        this@bahrSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = destination,
            enter = fadeIn(tween(BahrMotion.Medium, easing = BahrMotion.Standard)),
            exit = fadeOut(tween(BahrMotion.Medium, easing = BahrMotion.Standard)),
            // The same duration and easing as the screen transition around it, so they land together.
            boundsTransform = { _, _ -> tween(BahrMotion.Medium, easing = BahrMotion.Standard) },
        )
    }
}
