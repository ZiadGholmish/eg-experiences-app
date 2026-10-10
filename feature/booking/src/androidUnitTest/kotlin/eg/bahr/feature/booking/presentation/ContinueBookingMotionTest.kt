package eg.bahr.feature.booking.presentation

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
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

        compose.mainClock.advanceTimeBy(BahrMotion.Medium.toLong() + SETTLE_MARGIN_MILLIS)
        compose.onNodeWithTag(CONTINUE_BOOKING_TAG).assertDoesNotExist()
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

    private fun show(reducedMotion: Boolean) {
        val viewModel = ContinueBookingViewModel(store, repository, clock)
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English, reducedMotion = reducedMotion) {
                    ContinueBookingCard(onOpen = {}, viewModel = viewModel)
                }
            }
        }
        compose.waitForIdle()
        // The card fades in as the first answer lands; let it finish.
        compose.mainClock.advanceTimeBy(BahrMotion.Medium.toLong() + SETTLE_MARGIN_MILLIS)
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

    private companion object {
        /** What the hold has left when Home first reads it. */
        const val SECONDS_LEFT = 3

        /** A few frames past an animation's end. */
        const val SETTLE_MARGIN_MILLIS = 64L
    }
}
