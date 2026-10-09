package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureFullScreen
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlinx.coroutines.awaitCancellation
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Held seats × {ar, en} × {loading, loaded, checking at 00:00, leave dialog}, through the real view
 * model on a fake repository and a clock that only moves when the test moves it, so the countdown in
 * each golden is fixed.
 *
 * - loading: the re-read has not answered; the countdown and total (from the route) draw, the summary
 *   is a skeleton.
 * - loaded: the server answered 8 seconds after the hold (14:52, the handoff's value).
 * - checking: the deadline passed and the re-read failed: 00:00 in error colour, "trying again".
 * - leave: Back asked "Leave and release your seats?".
 *
 * Record: `./gradlew :feature:booking:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h740dp-xhdpi")
class HoldScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    /** The device clock; stands still unless a test moves it. */
    private var now = Instant.parse("2026-10-09T19:27:30Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = this@HoldScreenshotTest.now
        }

    private val hold =
        HoldRoute(
            ref = "BRL-7K4M2X9P",
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
            totalAmount = 900,
            totalCurrency = "EGP",
            guestPhone = "01012345678",
        )

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun loadingArabic() = snapLoading(AppLanguage.ARABIC)

    @Test
    fun loadingEnglish() = snapLoading(AppLanguage.ENGLISH)

    @Test
    fun loadedArabic() = snapLoaded(AppLanguage.ARABIC)

    @Test
    fun loadedEnglish() = snapLoaded(AppLanguage.ENGLISH)

    @Test
    fun checkingArabic() = snapChecking(AppLanguage.ARABIC)

    @Test
    fun checkingEnglish() = snapChecking(AppLanguage.ENGLISH)

    @Test
    fun leaveArabic() = snapLeave(AppLanguage.ARABIC)

    @Test
    fun leaveEnglish() = snapLeave(AppLanguage.ENGLISH)

    private fun snapLoading(language: AppLanguage) {
        show(language, HoldViewModel(hold, FakeBookingRepository(heldBooking = { _, _ -> awaitCancellation() }), clock))
        capture("hold_loading", language)
    }

    private fun snapLoaded(language: AppLanguage) {
        show(language, HoldViewModel(hold, answering(language), clock))
        capture("hold_loaded", language)
    }

    private fun snapChecking(language: AppLanguage) {
        val vm = HoldViewModel(hold, FakeBookingRepository(heldBooking = { _, _ -> AppResult.Failure(AppError.Network) }), clock)
        // The phone was away past the deadline; on return the re-read cannot get through.
        now += 16.minutes
        show(language, vm)
        capture("hold_checking", language)
    }

    private fun snapLeave(language: AppLanguage) {
        val vm = HoldViewModel(hold, answering(language), clock)
        show(language, vm)
        compose.runOnUiThread { vm.requestLeave() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.captureFullScreen("hold_leave_${language.tag}")
    }

    /** The re-read, answered 8 seconds after the hold was placed. */
    private fun answering(language: AppLanguage): FakeBookingRepository {
        val arabic = language == AppLanguage.ARABIC
        val booking =
            BookingFixtures.heldBooking(
                serverNow = (Instant.parse("2026-10-09T19:27:30Z") + 8.seconds).toString(),
                title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                dayLabel = if (arabic) "السبت 10 أكتوبر" else "Sat 10 Oct",
                city = if (arabic) "القاهرة" else "Cairo",
                placeName = if (arabic) "عبد المنعم رياض" else "Abdel Moneim Riad",
            )
        return FakeBookingRepository(heldBooking = { _, _ -> AppResult.Success(booking) })
    }

    private fun show(
        language: AppLanguage,
        viewModel: HoldViewModel,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    // The test activity (androidx.activity 1.10) provides no back dispatcher; the app's
                    // (1.12) does. A root one stands in, so the screen's back handler has one to join.
                    CompositionLocalProvider(
                        LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null),
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            HoldScreen(hold = hold, onEnded = {}, viewModel = viewModel)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun capture(
        prefix: String,
        language: AppLanguage,
    ) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
