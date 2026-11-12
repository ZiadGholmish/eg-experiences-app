package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeBookingRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Booking confirmed × {ar, en} × {light, dark}, loaded (M3-M8b: the success check over the reference). The
 * check (BahrLottie, played once) is shot still: reduced motion shows the frame it rests on.
 *
 * Record: `./gradlew :feature:booking:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h740dp-xhdpi")
class BookingConfirmedScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadedArabic() = shoot(AppLanguage.ARABIC)

    @Test
    fun loadedEnglish() = shoot(AppLanguage.ENGLISH)

    @Test
    fun loadedDarkArabic() = shoot(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun loadedDarkEnglish() = shoot(AppLanguage.ENGLISH, BahrThemeName.Dark)

    /** A test-cash booking (MV-M4): "Paid with Test cash" and a "Test" badge by the status. */
    @Test
    fun testCashArabic() = shoot(AppLanguage.ARABIC, method = "test_cash")

    @Test
    fun testCashEnglish() = shoot(AppLanguage.ENGLISH, method = "test_cash")

    /**
     * The contract's `Booking` (since MV-M4; before, a Java-era flat shape). Without [method] the page is the one shot
     * before the switch, pixel for pixel.
     */
    private fun shoot(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
        method: String? = null,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val booking =
            BookingFixtures.heldBooking(
                status = "CONFIRMED",
                title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                placeName = if (arabic) "عبد المنعم رياض" else "Abdel Moneim Riad",
                method = method,
            )
        val viewModel = BookingConfirmedViewModel(REF, FakeBookingRepository(bookingByRef = { AppResult.Success(booking) }))
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (arabic) BahrLocale.Arabic else BahrLocale.English,
                    theme = theme,
                    reducedMotion = true,
                ) {
                    // The app's Scaffold sets onBackground as the content colour; a bare test root does not.
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            BookingConfirmedScreen(ref = REF, onDone = {}, viewModel = viewModel)
                        }
                    }
                }
            }
        }
        // The check's file is read off the main thread: idle alone would shoot its empty box.
        compose.waitUntil(LOTTIE_LOAD_TIMEOUT_MS) { compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty() }
        val name =
            when {
                method != null -> "booking_confirmed_$method"
                theme == BahrThemeName.Dark -> "booking_confirmed_dark"
                else -> "booking_confirmed"
            }
        compose.captureScreenshot("${name}_${language.tag}")
    }

    private companion object {
        const val REF = "BRL-7K4M2X9P"
        const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L
    }
}
