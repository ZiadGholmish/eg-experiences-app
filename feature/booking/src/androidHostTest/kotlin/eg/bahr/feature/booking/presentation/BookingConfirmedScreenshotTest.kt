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
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The booking's ticket (MV-M5) × {ar, en} × {light, dark}: confirmed (the celebration over the reference, every row,
 * where to stand, WhatsApp), paid with test cash (MV-M4's badge on the money row), and a cancelled booking opened by
 * its reference (no celebration, no "Booked"); and loading, in ar and en. The celebration (BahrLottie, played once) is shot still: reduced motion
 * shows the frame it rests on. Tall enough to show the whole ticket, as a screenshot of it would be scrolled.
 *
 * Record: `./gradlew :feature:booking:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h1100dp-xhdpi")
class BookingConfirmedScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun confirmedArabic() = shoot(AppLanguage.ARABIC, Case.Confirmed)

    @Test
    fun confirmedEnglish() = shoot(AppLanguage.ENGLISH, Case.Confirmed)

    @Test
    fun confirmedDarkArabic() = shoot(AppLanguage.ARABIC, Case.Confirmed, BahrThemeName.Dark)

    @Test
    fun confirmedDarkEnglish() = shoot(AppLanguage.ENGLISH, Case.Confirmed, BahrThemeName.Dark)

    @Test
    fun testCashArabic() = shoot(AppLanguage.ARABIC, Case.TestCash)

    @Test
    fun testCashEnglish() = shoot(AppLanguage.ENGLISH, Case.TestCash)

    @Test
    fun testCashDarkArabic() = shoot(AppLanguage.ARABIC, Case.TestCash, BahrThemeName.Dark)

    @Test
    fun testCashDarkEnglish() = shoot(AppLanguage.ENGLISH, Case.TestCash, BahrThemeName.Dark)

    @Test
    fun cancelledArabic() = shoot(AppLanguage.ARABIC, Case.Cancelled)

    @Test
    fun cancelledEnglish() = shoot(AppLanguage.ENGLISH, Case.Cancelled)

    @Test
    fun cancelledDarkArabic() = shoot(AppLanguage.ARABIC, Case.Cancelled, BahrThemeName.Dark)

    @Test
    fun cancelledDarkEnglish() = shoot(AppLanguage.ENGLISH, Case.Cancelled, BahrThemeName.Dark)

    /**
     * Paid, then cancelled, with a host in the payload (MV-M5 review #3). The server sends `host` only on CONFIRMED and
     * COMPLETED, so this pins what the ticket does if one arrives anyway: still "Cancelled", no celebration, no "show this
     * screen", no where to stand; the money row says how it was paid, and the host row and WhatsApp show what was sent.
     */
    @Test
    fun cancelledWithHostArabic() = shoot(AppLanguage.ARABIC, Case.CancelledWithHost)

    @Test
    fun cancelledWithHostEnglish() = shoot(AppLanguage.ENGLISH, Case.CancelledWithHost)

    /** While the booking is read: the header's ground with shimmering places, no status text yet (M3-M14). */
    @Test
    fun loadingArabic() = shootLoading(AppLanguage.ARABIC)

    @Test
    fun loadingEnglish() = shootLoading(AppLanguage.ENGLISH)

    private fun shootLoading(language: AppLanguage) {
        val viewModel = BookingConfirmedViewModel(REF, FakeBookingRepository(bookingByRef = { awaitCancellation() }))
        setScreen(language, BahrThemeName.LakeBurullus, viewModel)
        compose.captureScreenshot("ticket_loading_${language.tag}")
    }

    private enum class Case(
        val golden: String,
        val status: String,
        val method: String?,
    ) {
        Confirmed("ticket_confirmed", "CONFIRMED", "vodafone_cash"),
        TestCash("ticket_test_cash", "CONFIRMED", "test_cash"),

        /** Released before paying, opened later by its reference (a notification). */
        Cancelled("ticket_cancelled", "CANCELLED", null),
        CancelledWithHost("ticket_cancelled_with_host", "CANCELLED", "card"),
    }

    private fun shoot(
        language: AppLanguage,
        case: Case,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val booking =
            BookingFixtures
                .heldBooking(
                    status = case.status,
                    title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                    dayLabel = if (arabic) "السبت 10 أكتوبر" else "Sat 10 Oct",
                    city = if (arabic) "القاهرة" else "Cairo",
                    placeName = if (arabic) "عبد المنعم رياض" else "Abdel Moneim Riad",
                    method = case.method,
                    whereToStand = if (arabic) WHERE_AR else WHERE_EN,
                    host = BookingFixtures.host(if (arabic) "أشرف البحر" else "Ashraf El Bahr"),
                ).let {
                    // The fixture drops the host off an unpaid status, as the server does; this case puts it back.
                    if (case ==
                        Case.CancelledWithHost
                    ) {
                        it.copy(host = BookingFixtures.host(if (arabic) "أشرف البحر" else "Ashraf El Bahr"))
                    } else {
                        it
                    }
                }
        val viewModel = BookingConfirmedViewModel(REF, FakeBookingRepository(bookingByRef = { AppResult.Success(booking) }))
        setScreen(language, theme, viewModel)
        if (case == Case.Confirmed || case == Case.TestCash) {
            // The celebration's file is read off the main thread: idle alone would shoot its empty box.
            compose.waitUntil(
                LOTTIE_LOAD_TIMEOUT_MS,
            ) { compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty() }
        }
        val dark = if (theme == BahrThemeName.Dark) "_dark" else ""
        compose.captureScreenshot("${case.golden}${dark}_${language.tag}")
    }

    private fun setScreen(
        language: AppLanguage,
        theme: BahrThemeName,
        viewModel: BookingConfirmedViewModel,
    ) {
        val arabic = language == AppLanguage.ARABIC
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
    }

    private companion object {
        const val REF = "BRL-7K4M2X9P"
        const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L
        const val WHERE_EN =
            "Abdel Moneim Riad terminal, the bus bay behind the Ramses Hilton. The bus is white with a red stripe and a " +
                "paper sign: البرلس."
        const val WHERE_AR = "موقف عبد المنعم رياض، رصيف الأتوبيسات خلف رمسيس هيلتون. الأتوبيس أبيض بخط أحمر وعليه ورقة مكتوب فيها: البرلس."
    }
}
