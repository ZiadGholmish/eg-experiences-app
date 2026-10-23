package eg.bahr.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BahrLottie costs no frames when nothing visibly moves (M3-M8; the iPhone ran hot): a played-once
 * animation stops asking for frames when it rests; a looping one plays while on screen and asks for none
 * while scrolled out of view or with the app in the background; under reduced motion nothing plays.
 * "Asks for frames" is the recomposer's pending work, which includes every coroutine waiting on the next
 * frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class BahrLottieFramesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playedOnceStopsAskingForFramesAtRest() {
        show(BahrLottie.Playback.Once)
        assertTrue(asksForFrames(), "it plays")
        compose.mainClock.advanceTimeBy(LONGER_THAN_EITHER_ANIMATION_MS)
        assertFalse(asksForFrames(), "at rest, no more frames")
    }

    @Test
    fun loopPlaysWhileOnScreen() {
        show(BahrLottie.Playback.Loop)
        compose.mainClock.advanceTimeBy(LONGER_THAN_EITHER_ANIMATION_MS)
        assertTrue(asksForFrames())
    }

    @Test
    fun loopOutOfViewAsksForNoFrames() {
        show(BahrLottie.Playback.Loop, offScreen = true)
        compose.mainClock.advanceTimeBy(LONGER_THAN_EITHER_ANIMATION_MS)
        assertFalse(asksForFrames())
    }

    @Test
    fun reducedMotionAsksForNoFrames() {
        show(BahrLottie.Playback.Loop, reducedMotion = true)
        compose.mainClock.advanceTimeBy(LONGER_THAN_EITHER_ANIMATION_MS)
        assertFalse(asksForFrames())
    }

    /** The app in the background (stopped): paused; back in the foreground: playing again. */
    @Test
    fun loopPausesInTheBackground() {
        val owner = TestLifecycleOwner()
        show(BahrLottie.Playback.Loop, lifecycleOwner = owner)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeBy(LONGER_THAN_EITHER_ANIMATION_MS)
        assertFalse(asksForFrames(), "paused in the background")
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertTrue(asksForFrames(), "playing again in the foreground")
    }

    private fun show(
        playback: BahrLottie.Playback,
        offScreen: Boolean = false,
        reducedMotion: Boolean = false,
        lifecycleOwner: LifecycleOwner? = null,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (lifecycleOwner == null) {
                Sample(playback, offScreen, reducedMotion)
            } else {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) { Sample(playback, offScreen, reducedMotion) }
            }
        }
        // The file loads off the main thread; frames carry its result into the composition.
        compose.waitUntil(LOAD_TIMEOUT_MS) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Composable
    private fun Sample(
        playback: BahrLottie.Playback,
        offScreen: Boolean,
        reducedMotion: Boolean,
    ) {
        BahrTheme(locale = BahrLocale.English, reducedMotion = reducedMotion) {
            Box(Modifier.fillMaxSize()) {
                BahrLottie(
                    modifier = Modifier.offset(y = if (offScreen) OFF_SCREEN else 0.dp).width(BahrSize.lottieState),
                    props = BahrLottie.Props(BahrLottie.Animation.Bell, playback = playback),
                )
            }
        }
    }

    /** A lifecycle the test moves by hand, starting resumed. */
    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun asksForFrames(): Boolean = Recomposer.runningRecomposers.value.any { it.hasPendingWork }

    private companion object {
        const val LONGER_THAN_EITHER_ANIMATION_MS = 5_000L
        const val LOAD_TIMEOUT_MS = 10_000L

        /** Well below the 640dp test screen. */
        val OFF_SCREEN = 2_000.dp
    }
}
