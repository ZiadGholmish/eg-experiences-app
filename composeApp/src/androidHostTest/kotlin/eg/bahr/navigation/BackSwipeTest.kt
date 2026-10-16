package eg.bahr.navigation

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import eg.bahr.core.designsystem.theme.BahrBackSwipe
import eg.bahr.core.designsystem.theme.bahrPopEnter
import eg.bahr.core.designsystem.theme.bahrPopExit
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * M3-M12: a back swipe reaches the screen transitions as one, with its edge, through the same
 * dispatcher AppNavHost reads; without it a swiped pop would run on the button's curve and run ahead of
 * the finger. Android's predictive back here; iOS's edge swipe feeds the same dispatcher (not run here).
 *
 * M3-M10: navigation 2.10 hands a swiped pop to the NavHost's `predictivePop*` transitions with the
 * edge. The pages sit in [AppScreenNavHost], the same NavHost call AppNavHost makes, and the test checks
 * the edge reaches [AppScreenTransitions]' pops: dropping the `predictivePop*` wiring there, or an
 * upgrade that stops passing the edge, fails it instead of quietly playing the library's own swipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi", application = Application::class)
class BackSwipeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var dispatcher: NavigationEventDispatcher? = null
    private lateinit var navController: NavHostController

    @Test
    fun `a swipe in progress is seen with its edge, and is gone once it ends`() {
        showTwoPages()
        assertEquals(BahrBackSwipe.None, dispatcher.backSwipe(), "no swipe yet: the Back button's pop")

        swipe(BackEventCompat.EDGE_LEFT)
        assertEquals(BahrBackSwipe.FromLeftEdge, dispatcher.backSwipe())

        cancelSwipe()
        assertEquals(BahrBackSwipe.None, dispatcher.backSwipe())

        swipe(BackEventCompat.EDGE_RIGHT)
        assertEquals(BahrBackSwipe.FromRightEdge, dispatcher.backSwipe())
    }

    @Test
    fun `a swiped pop reaches the NavHost's pop transitions with its edge, a Back button pop with none`() {
        val entering = mutableListOf<BahrBackSwipe>()
        val leaving = mutableListOf<BahrBackSwipe>()
        val transitions =
            AppScreenTransitions(
                reducedMotion = { false },
                backSwipe = { dispatcher.backSwipe() },
                pop = { reduced, swipe -> bahrPopEnter(reduced, swipe).also { entering += swipe } },
                popOut = { reduced, swipe -> bahrPopExit(reduced, swipe).also { leaving += swipe } },
            )
        showTwoPages(transitions)

        swipe(BackEventCompat.EDGE_LEFT)
        assertEquals(BahrBackSwipe.FromLeftEdge, entering.lastOrNull(), "the page coming back")
        assertEquals(BahrBackSwipe.FromLeftEdge, leaving.lastOrNull(), "the page leaving")

        cancelSwipe()
        swipe(BackEventCompat.EDGE_RIGHT)
        assertEquals(BahrBackSwipe.FromRightEdge, entering.lastOrNull())
        assertEquals(BahrBackSwipe.FromRightEdge, leaving.lastOrNull())

        cancelSwipe()
        entering.clear()
        leaving.clear()
        compose.runOnUiThread { navController.popBackStack() }
        compose.waitForIdle()
        assertEquals(listOf(BahrBackSwipe.None), entering.distinct(), "the Back button's pop")
        assertEquals(listOf(BahrBackSwipe.None), leaving.distinct())
    }

    /** Two pages in [AppScreenNavHost], the NavHost AppNavHost builds, the second on top. */
    private fun showTwoPages(
        transitions: AppScreenTransitions = AppScreenTransitions(reducedMotion = { false }, backSwipe = { dispatcher.backSwipe() }),
    ) {
        compose.setContent {
            dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
            navController = rememberNavController()
            AppScreenNavHost(navController, startDestination = FirstPage, transitions = transitions) {
                composable<FirstPage> {}
                composable<SecondPage> {}
            }
        }
        compose.runOnUiThread { navController.navigate(SecondPage) }
        compose.waitForIdle()
    }

    private fun swipe(edge: Int) {
        compose.runOnUiThread {
            val back = compose.activity.onBackPressedDispatcher
            back.dispatchOnBackStarted(BackEventCompat(TOUCH_X, TOUCH_Y, 0f, edge))
            back.dispatchOnBackProgressed(BackEventCompat(TOUCH_X, TOUCH_Y, HALF, edge))
        }
        compose.waitForIdle()
    }

    private fun cancelSwipe() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
    }

    private companion object {
        const val TOUCH_X = 10f
        const val TOUCH_Y = 400f
        const val HALF = .5f
    }
}

/** Typed routes, as AppScreenNavHost takes; top-level, since serialization cannot reach a private nested object. */
@Serializable
internal data object FirstPage

@Serializable
internal data object SecondPage
