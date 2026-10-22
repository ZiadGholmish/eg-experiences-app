package eg.bahr.core.designsystem.theme

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The shimmer clock (M3-M14) asks for frames only while a placeholder is attached, so a loaded screen
 * (or one under reduced motion) costs nothing; and it pulses between the handoff's .5 and .9.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BahrShimmerClockTest {
    /** A 60 Hz frame clock on the test's virtual time that counts the frames asked of it. */
    private class CountingFrameClock(
        private val scope: TestScope,
    ) : MonotonicFrameClock {
        var frames = 0

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MILLIS)
            frames++
            return onFrame(scope.testScheduler.currentTime * NANOS_PER_MILLI)
        }
    }

    @Test
    fun pulsesOnlyWhileAPlaceholderIsAttached() =
        runTest {
            val frames = CountingFrameClock(this)
            val clock = BahrShimmerClock()
            val job = launch(frames) { clock.run(reducedMotion = false) }
            settle()
            advanceTimeBy(BahrMotion.Shimmer.toLong())
            assertEquals(0, frames.frames, "no frames without a placeholder")

            clock.attach()
            settle()
            advanceTimeBy(BahrMotion.Shimmer / QUARTER)
            assertTrue(frames.frames > 0, "the pulse runs while a placeholder is attached")
            assertTrue(clock.alpha > BahrMotion.ShimmerAlphaLow && clock.alpha < BahrMotion.ShimmerAlphaHigh, "mid-pulse: ${clock.alpha}")

            clock.detach()
            settle()
            val afterDetach = frames.frames
            advanceTimeBy(BahrMotion.Shimmer.toLong())
            assertEquals(afterDetach, frames.frames, "the pulse stops with the last placeholder")
            assertEquals(BahrMotion.ShimmerAlphaLow, clock.alpha)
            job.cancel()
        }

    @Test
    fun staysStillUnderReducedMotion() =
        runTest {
            val frames = CountingFrameClock(this)
            val clock = BahrShimmerClock()
            val job = launch(frames) { clock.run(reducedMotion = true) }
            clock.attach()
            settle()
            advanceTimeBy(BahrMotion.Shimmer.toLong())
            assertEquals(0, frames.frames)
            assertEquals(BahrMotion.ShimmerAlphaStatic, clock.alpha)
            assertNotEquals(BahrMotion.ShimmerAlphaLow, clock.alpha)
            job.cancel()
        }

    /** Hands a state write to `snapshotFlow` (Compose's recomposer does this every frame in the app). */
    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
        const val QUARTER = 4L
    }
}
