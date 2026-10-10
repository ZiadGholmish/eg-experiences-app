package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.ANIMATION_SETTLE_MARGIN_MILLIS
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Home's "Continue your booking" card × {ar, en} × {loaded (10 minutes left), urgent (under a
 * minute, the time in the error colour), the last minute's pulse at its height}, through the real view model with a fake repository, store
 * and clock, laid out as on Home (gutter padding). The loading state draws nothing: asserted, not
 * captured.
 *
 * Record: `./gradlew :feature:booking:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class ContinueBookingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    /** When the hold was placed, by the server's clock. */
    private val placedAt = Instant.parse("2026-10-09T19:27:30Z")

    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-09T09:00:00Z")
        }

    private val stored =
        StoredHold(
            ref = "BRL-7K4M2X9P",
            guestPhone = "+201012345678",
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
        )

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun loadedArabic() = snap("continue_booking_loaded", AppLanguage.ARABIC, sinceHold = 5.minutes)

    @Test
    fun loadedEnglish() = snap("continue_booking_loaded", AppLanguage.ENGLISH, sinceHold = 5.minutes)

    // The last minute's pulse (M4-M4) loops, so it has no frame to call "at rest": the urgent goldens
    // are the still panel, as reduce motion draws it (and as the pulse starts).
    @Test
    fun urgentArabic() = snap("continue_booking_urgent", AppLanguage.ARABIC, sinceHold = LAST_MINUTE, reducedMotion = true)

    @Test
    fun urgentEnglish() = snap("continue_booking_urgent", AppLanguage.ENGLISH, sinceHold = LAST_MINUTE, reducedMotion = true)

    // ...and the pulse at its height: the timer's circle swollen and warmed to the error colour, its
    // icon and the text unchanged.
    @Test
    fun pulseArabic() =
        snap("continue_booking_pulse", AppLanguage.ARABIC, sinceHold = LAST_MINUTE, settleMillis = BahrMotion.CountdownPulse.toLong())

    @Test
    fun pulseEnglish() =
        snap("continue_booking_pulse", AppLanguage.ENGLISH, sinceHold = LAST_MINUTE, settleMillis = BahrMotion.CountdownPulse.toLong())

    @Test
    fun drawsNothingUntilTheServerAnswers() {
        show(AppLanguage.ARABIC, FakeBookingRepository(heldBooking = { _, _ -> awaitCancellation() }))
        compose.onNodeWithTag(CONTINUE_BOOKING_TAG).assertDoesNotExist()
    }

    // A multi-day hold (M4-B0b) names the day it is back: "from Sat 10 Oct to Sun 11 Oct".
    @Test
    fun multiDayArabic() = snap("continue_booking_multi_day", AppLanguage.ARABIC, sinceHold = 5.minutes, returnDate = SUNDAY)

    @Test
    fun multiDayEnglish() = snap("continue_booking_multi_day", AppLanguage.ENGLISH, sinceHold = 5.minutes, returnDate = SUNDAY)

    private fun snap(
        prefix: String,
        language: AppLanguage,
        sinceHold: Duration,
        returnDate: LocalDate? = null,
        reducedMotion: Boolean = false,
        settleMillis: Long = BahrMotion.Medium.toLong() + ANIMATION_SETTLE_MARGIN_MILLIS,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val booking =
            BookingFixtures.heldBooking(
                holdExpiresAt = (placedAt + 15.minutes).toString(),
                serverNow = (placedAt + sinceHold).toString(),
                title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                dayLabel = if (arabic) "السبت 10 أكتوبر" else "Sat 10 Oct",
                returnDate = returnDate,
            )
        show(language, FakeBookingRepository(heldBooking = { _, _ -> AppResult.Success(booking) }), reducedMotion, settleMillis)
        compose.onNodeWithTag(CONTINUE_BOOKING_TAG).assertExists()
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private fun show(
        language: AppLanguage,
        repository: FakeBookingRepository,
        reducedMotion: Boolean = false,
        settleMillis: Long = BahrMotion.Medium.toLong() + ANIMATION_SETTLE_MARGIN_MILLIS,
    ) {
        val viewModel = ContinueBookingViewModel(FakeActiveHoldStore(stored), repository, clock)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                    reducedMotion = reducedMotion,
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.xl),
                    ) {
                        ContinueBookingCard(onOpen = {}, viewModel = viewModel)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        // The card fades in as the answer lands (M4-M4); the goldens are the card at rest.
        compose.mainClock.advanceTimeBy(settleMillis)
        compose.waitForIdle()
    }

    private companion object {
        val SUNDAY = LocalDate(2026, 10, 11)

        /** 42 seconds left: under 2 minutes (the time in the error colour) and in the last minute (the pulse). */
        val LAST_MINUTE = 14.minutes + 18.seconds
    }
}
