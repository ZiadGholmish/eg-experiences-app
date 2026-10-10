package eg.bahr.feature.booking.presentation

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.ANIMATION_SETTLE_MARGIN_MILLIS
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.model.HeldBookingDto
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaDuration

/**
 * Home's "Continue your booking" card leaving when the hold runs out (M4-M4): the real view model
 * over a fake repository, store and clock. At zero the view model reads the booking again, the
 * server says EXPIRED, and the card goes: animated (still drawn a frame later, gone once the
 * animation is over), or at once under reduce motion.
 *
 * The view model ticks on the main looper (`delay`), which Robolectric only moves when told, so the
 * test moves it ([tick]) as well as the device clock ([now]) and the frame clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class ContinueBookingMotionTest {
    @get:Rule
    val compose = createComposeRule()

    /** When the hold was placed, by the server's clock. */
    private val placedAt = Instant.parse("2026-10-09T19:27:30Z")

    /** The device clock; stands still unless a test moves it. */
    private var now = Instant.parse("2026-10-09T09:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = this@ContinueBookingMotionTest.now
        }

    private val stored =
        StoredHold(
            ref = "BRL-7K4M2X9P",
            guestPhone = "+201012345678",
            holdExpiresAt = (placedAt + 15.minutes).toString(),
            serverNow = placedAt.toString(),
        )

    /** The server's answers, in order: held with [SECONDS_LEFT] to go, then expired. */
    private val answers =
        ArrayDeque(
            listOf(
                heldBooking("HELD", serverNow = placedAt + 15.minutes - SECONDS_LEFT.seconds),
                heldBooking("EXPIRED", serverNow = placedAt + 15.minutes + 1.seconds),
            ),
        )
    private val store = FakeActiveHoldStore(stored)
    private val repository = FakeBookingRepository(heldBooking = { _, _ -> AppResult.Success(answers.removeFirst()) })

    @Before
    fun setUp() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun `the card animates away once the hold has expired`() {
        show(reducedMotion = false)
        compose.onNodeWithTag(CONTINUE_BOOKING_TAG).assertIsDisplayed()

        expire()
        // The view model has already let the hold go: what is still drawn is the exit animation.
        assertEquals(listOf(stored.ref), store.clears, "an expired hold is forgotten")
        assertNull(store.hold.value)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(CONTINUE_BOOKING_TAG).assertExists()

        compose.mainClock.advanceTimeBy(BahrMotion.Medium.toLong() + ANIMATION_SETTLE_MARGIN_MILLIS)
        compose.onNodeWithTag(CONTINUE_BOOKING_TAG).assertDoesNotExist()
    }

    /**
     * Nothing below the card jumps (M4-M4 review #5): what sits under the slot (Home's list) moves up
     * frame by frame as the card leaves, never down and never in one step, and ends where the slot
     * started, the gap above the card gone with it.
     */
    @Test
    fun `what is below moves up smoothly as the card leaves - never jumps`() {
        show(reducedMotion = false, withBelow = true)
        val start = belowTop()
        val slotTop = compose.onNodeWithTag(SLOT).getBoundsInRoot().top

        expire()
        val tops = mutableListOf(start)
        repeat(FRAMES_TO_SAMPLE) {
            compose.mainClock.advanceTimeByFrame()
            tops += belowTop()
        }

        assertTrue(tops.zipWithNext().all { (before, after) -> after <= before }, "never moves down: $tops")
        val steps = tops.zipWithNext { before, after -> before - after }
        val travel = start - slotTop
        assertTrue(steps.all { it < travel / 2 }, "no single frame covers half the way: $steps")
        assertEquals(slotTop, tops.last(), "ends where the slot began: $tops")
    }

    @Test
    fun `under reduce motion the card goes at once`() {
        show(reducedMotion = true)
        compose.onNodeWithTag(CONTINUE_BOOKING_TAG).assertIsDisplayed()

        expire()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(CONTINUE_BOOKING_TAG).assertDoesNotExist()
    }

    /** The deadline passes on the device's clock; the next tick sees zero and the re-read says EXPIRED. */
    private fun expire() {
        now += (SECONDS_LEFT + 1).seconds
        tick()
        compose.waitForIdle()
        assertEquals(2, repository.bookingReads.size, "at zero the booking is read again")
    }

    /** Lets the view model's ticker run once (it waits [HoldViewModel.TICK] between ticks). */
    private fun tick() = shadowOf(Looper.getMainLooper()).idleFor(HoldViewModel.TICK.toJavaDuration())

    private fun show(
        reducedMotion: Boolean,
        withBelow: Boolean = false,
    ) {
        val viewModel = ContinueBookingViewModel(store, repository, clock)
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English, reducedMotion = reducedMotion) {
                    if (withBelow) {
                        // As Home hosts it: the slot's gap handed to the card, the list right under it.
                        Column {
                            Box(
                                Modifier.testTag(SLOT),
                            ) { ContinueBookingCard(onOpen = {}, modifier = Modifier.padding(top = GAP), viewModel = viewModel) }
                            Box(Modifier.fillMaxWidth().height(GAP).testTag(BELOW))
                        }
                    } else {
                        ContinueBookingCard(onOpen = {}, viewModel = viewModel)
                    }
                }
            }
        }
        compose.waitForIdle()
        // The card fades in as the first answer lands; let it finish.
        compose.mainClock.advanceTimeBy(BahrMotion.Medium.toLong() + ANIMATION_SETTLE_MARGIN_MILLIS)
        compose.waitForIdle()
    }

    private fun heldBooking(
        status: String,
        serverNow: Instant,
    ): HeldBookingDto =
        BookingFixtures.heldBooking(
            status = status,
            holdExpiresAt = (placedAt + 15.minutes).toString(),
            serverNow = serverNow.toString(),
        )

    private fun belowTop(): Dp = compose.onNodeWithTag(BELOW).getBoundsInRoot().top

    private companion object {
        /** What the hold has left when Home first reads it. */
        const val SECONDS_LEFT = 3
        const val SLOT = "slot"
        const val BELOW = "below"
        val GAP = BahrSpacing.lg

        /** Past the exit's [BahrMotion.Medium] at 60 fps, with a few frames to spare. */
        const val FRAMES_TO_SAMPLE = 24
    }
}
