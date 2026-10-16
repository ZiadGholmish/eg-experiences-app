package eg.bahr.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventTransitionState
import eg.bahr.core.designsystem.theme.BahrBackSwipe
import eg.bahr.core.designsystem.theme.bahrPopEnter
import eg.bahr.core.designsystem.theme.bahrPopExit
import eg.bahr.core.designsystem.theme.bahrPushEnter
import eg.bahr.core.designsystem.theme.bahrPushExit
import eg.bahr.core.designsystem.theme.bahrReplaceEnter
import eg.bahr.core.designsystem.theme.bahrReplaceExit
import eg.bahr.feature.splash.navigation.SplashRoute

/** A pop's screen transition for reduce motion and a [BahrBackSwipe]. */
internal typealias PopEnter = AnimatedContentTransitionScope<*>.(reducedMotion: Boolean, swipe: BahrBackSwipe) -> EnterTransition

/** The leaving screen's side of [PopEnter]. */
internal typealias PopExit = AnimatedContentTransitionScope<*>.(reducedMotion: Boolean, swipe: BahrBackSwipe) -> ExitTransition

/**
 * The transitions a tab's NavHost gets (M3-M12; M3-M10 for navigation 2.10): one for every push and pop,
 * in the platform's style; destinations set none. Splash is replaced by Home rather than covered by it.
 *
 * Navigation 2.10 runs a back swipe (Android's predictive back, iOS's edge swipe) through its own pair,
 * [predictivePopEnter]/[predictivePopExit], with the edge the swipe started from; left unset it would
 * play the library's default instead of ours. [popEnter]/[popExit] are then the Back button's and a
 * code pop's, which still ask [backSwipe] in case a pop lands while a swipe is under way.
 *
 * [AppScreenNavHost] is the one place they are handed to a NavHost, for AppNavHost and BackSwipeTest alike;
 * the design system's pops are injectable for that test only.
 */
internal class AppScreenTransitions(
    private val reducedMotion: () -> Boolean,
    private val backSwipe: () -> BahrBackSwipe,
    private val pop: PopEnter = { reduced, swipe -> bahrPopEnter(reduced, swipe) },
    private val popOut: PopExit = { reduced, swipe -> bahrPopExit(reduced, swipe) },
) {
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (initialState.destination.hasRoute<SplashRoute>()) bahrReplaceEnter(reducedMotion()) else bahrPushEnter(reducedMotion())
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (initialState.destination.hasRoute<SplashRoute>()) bahrReplaceExit(reducedMotion()) else bahrPushExit(reducedMotion())
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { pop(reducedMotion(), backSwipe()) }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { popOut(reducedMotion(), backSwipe()) }

    val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { edge ->
        pop(reducedMotion(), edge.toBackSwipe())
    }

    val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { edge ->
        popOut(reducedMotion(), edge.toBackSwipe())
    }
}

/**
 * A tab's NavHost with [transitions] wired in (M3-M10): every one of the six transition arguments, the
 * `predictivePop*` pair included. AppNavHost builds its graph through this, and BackSwipeTest drives a
 * swipe through it, so dropping an argument here fails that test rather than silently bringing back the
 * library's fade-and-shrink on every swipe back.
 */
@Composable
internal fun AppScreenNavHost(
    navController: NavHostController,
    startDestination: Any,
    transitions: AppScreenTransitions,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
        predictivePopEnterTransition = transitions.predictivePopEnter,
        predictivePopExitTransition = transitions.predictivePopExit,
        builder = builder,
    )
}

/**
 * The back swipe in progress, if any (M3-M12): iOS's edge swipe and Android's predictive back both reach
 * the NavHost through this dispatcher, whose state carries the edge the finger started from. The
 * destinations' dim reads it. Internal for BackSwipeTest.
 */
internal fun NavigationEventDispatcher?.backSwipe(): BahrBackSwipe {
    val swipe = (this?.transitionState?.value as? NavigationEventTransitionState.InProgress)?.latestEvent ?: return BahrBackSwipe.None
    return swipe.swipeEdge.toBackSwipe()
}

/** A navigation event's swipe edge as the screen transitions take it. */
private fun Int.toBackSwipe(): BahrBackSwipe =
    when (this) {
        NavigationEvent.EDGE_LEFT -> BahrBackSwipe.FromLeftEdge
        NavigationEvent.EDGE_RIGHT -> BahrBackSwipe.FromRightEdge
        // No edge: not a swipe (a key, a button), so the ordinary pop.
        else -> BahrBackSwipe.None
    }
