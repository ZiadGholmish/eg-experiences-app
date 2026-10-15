package eg.bahr.navigation

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import eg.bahr.core.designsystem.theme.BahrBackSwipe
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

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(BahrBackSwipe.None, dispatcher.backSwipe())

        swipe(BackEventCompat.EDGE_RIGHT)
        assertEquals(BahrBackSwipe.FromRightEdge, dispatcher.backSwipe())
    }

    private fun showTwoPages() {
        compose.setContent {
            dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
            navController = rememberNavController()
            NavHost(navController, startDestination = FIRST) {
                composable(FIRST) {}
                composable(SECOND) {}
            }
        }
        compose.runOnUiThread { navController.navigate(SECOND) }
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

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val TOUCH_X = 10f
        const val TOUCH_Y = 400f
        const val HALF = .5f
    }
}
