package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
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
import kotlin.test.assertEquals

/**
 * The booking's ticket (MV-M5) × {ar, en} × {light, dark}: confirmed (the celebration over the reference, every row,
 * where to stand, WhatsApp), paid with test cash (MV-M4's badge on the money row), and a cancelled booking opened by
 * its reference (no celebration, no "Booked"); and loading, in ar and en. Since MV-M6 also as a booking's details from My
 * trips, in ar and en: upcoming, past with "Rate your trip", cancelled, loading and not found. The celebration (BahrLottie, played once) is shot still: reduced motion
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

    // ---------- From My trips (MV-M6): "Booking details" with a back button, the trip's page instead of "Trips" ----------

    /** An upcoming booking: the ticket, then WhatsApp and "View trip"; no "Trips" (Back is the way to My trips). */
    @Test
    fun detailsUpcomingArabic() = shoot(AppLanguage.ARABIC, Case.DetailsUpcoming)

    @Test
    fun detailsUpcomingEnglish() = shoot(AppLanguage.ENGLISH, Case.DetailsUpcoming)

    /** A finished trip My trips says may be rated: "Rate your trip" over "View trip". */
    @Test
    fun detailsPastRateArabic() = shoot(AppLanguage.ARABIC, Case.DetailsPastRate)

    @Test
    fun detailsPastRateEnglish() = shoot(AppLanguage.ENGLISH, Case.DetailsPastRate)

    /** Cancelled after paying, as Past lists it: no celebration, no rating, still a way to the trip. */
    @Test
    fun detailsCancelledArabic() = shoot(AppLanguage.ARABIC, Case.DetailsCancelled)

    @Test
    fun detailsCancelledEnglish() = shoot(AppLanguage.ENGLISH, Case.DetailsCancelled)

    /** Read while the details open: the bar (back button, "Booking details") is there from the first frame. */
    @Test
    fun detailsLoadingArabic() = shootLoading(AppLanguage.ARABIC, TicketEntry.Details)

    @Test
    fun detailsLoadingEnglish() = shootLoading(AppLanguage.ENGLISH, TicketEntry.Details)

    /** While the booking is read the bar's back button stays a button for screen readers (outside the skeleton's region). */
    @Test
    fun `details loading - Back is reachable by screen readers`() {
        var backs = 0
        val viewModel = BookingConfirmedViewModel(REF, FakeBookingRepository(bookingByRef = { awaitCancellation() }))
        compose.setContent {
            BahrTheme(locale = BahrLocale.English, reducedMotion = true) {
                BookingConfirmedScreen(
                    ref = REF,
                    actions = TicketCallbacks(onBack = { backs++ }),
                    entry = TicketEntry.Details,
                    viewModel = viewModel,
                )
            }
        }
        compose.onNodeWithText("Booking details").assertExists()
        compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()
        assertEquals(1, backs)
    }

    /** Not found (a 404, not worth retrying): the bar on the page's ground over the message, so Back is never lost. */
    @Test
    fun detailsNotFoundArabic() = shootNotFound(AppLanguage.ARABIC)

    @Test
    fun detailsNotFoundEnglish() = shootNotFound(AppLanguage.ENGLISH)

    /** MV-M6: the details' buttons hand over the booking's trip and reference; no "Trips" there, which Back repeats. */
    @Test
    fun `details - View trip and Rate call back with the booking's trip and reference - Back too`() {
        val trips = mutableListOf<String>()
        val rated = mutableListOf<Pair<String, String?>>()
        var backs = 0
        val booking = BookingFixtures.heldBooking(status = "COMPLETED", title = "Dawn on Lake Burullus")
        val viewModel = BookingConfirmedViewModel(REF, FakeBookingRepository(bookingByRef = { AppResult.Success(booking) }))
        compose.setContent {
            BahrTheme(locale = BahrLocale.English, reducedMotion = true) {
                BookingConfirmedScreen(
                    ref = REF,
                    actions =
                        TicketCallbacks(
                            onBack = { backs++ },
                            onViewTrip = { trips += it },
                            onRateTrip = { ref, title -> rated += ref to title },
                        ),
                    entry = TicketEntry.Details,
                    canRate = true,
                    viewModel = viewModel,
                )
            }
        }
        compose.onNodeWithText("Booking details").assertExists()
        compose.onNodeWithText("Trips").assertDoesNotExist()
        compose.onNodeWithTag(TICKET_VIEW_TRIP_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(TICKET_RATE_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(TICKET_BACK_TAG).performScrollTo().performClick()
        assertEquals(listOf(BookingFixtures.SLUG), trips)
        assertEquals(listOf<Pair<String, String?>>(booking.ref to "Dawn on Lake Burullus"), rated)
        assertEquals(1, backs)
    }

    /** After paying: "Booking confirmation" and "Trips", no trip page or rating offered. */
    @Test
    fun `after paying - Trips and no View trip or Rate`() {
        var done = 0
        val booking = BookingFixtures.heldBooking(status = "CONFIRMED")
        val viewModel = BookingConfirmedViewModel(REF, FakeBookingRepository(bookingByRef = { AppResult.Success(booking) }))
        compose.setContent {
            BahrTheme(locale = BahrLocale.English, reducedMotion = true) {
                BookingConfirmedScreen(ref = REF, actions = TicketCallbacks(onDone = { done++ }), canRate = true, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("Booking confirmation").assertExists()
        compose.onNodeWithTag(TICKET_VIEW_TRIP_TAG).assertDoesNotExist()
        compose.onNodeWithTag(TICKET_RATE_TAG).assertDoesNotExist()
        compose.onNodeWithText("Trips").performScrollTo().performClick()
        assertEquals(1, done)
    }

    /** While the booking is read: the header's ground with shimmering places, no status text yet (M3-M14). */
    @Test
    fun loadingArabic() = shootLoading(AppLanguage.ARABIC)

    @Test
    fun loadingEnglish() = shootLoading(AppLanguage.ENGLISH)

    private fun shootLoading(
        language: AppLanguage,
        entry: TicketEntry = TicketEntry.Paid,
    ) {
        val viewModel = BookingConfirmedViewModel(REF, FakeBookingRepository(bookingByRef = { awaitCancellation() }))
        setScreen(language, BahrThemeName.LakeBurullus, viewModel, entry)
        val prefix = if (entry == TicketEntry.Details) "ticket_details_loading" else "ticket_loading"
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private fun shootNotFound(language: AppLanguage) {
        val notFound = AppError.Api(code = ApiErrorCodes.NOT_FOUND, message = null, httpStatus = NOT_FOUND_STATUS)
        val viewModel = BookingConfirmedViewModel(REF, FakeBookingRepository(bookingByRef = { AppResult.Failure(notFound) }))
        setScreen(language, BahrThemeName.LakeBurullus, viewModel, TicketEntry.Details)
        compose.captureScreenshot("ticket_details_not_found_${language.tag}")
    }

    private enum class Case(
        val golden: String,
        val status: String,
        val method: String?,
        val entry: TicketEntry = TicketEntry.Paid,
        val canRate: Boolean = false,
    ) {
        Confirmed("ticket_confirmed", "CONFIRMED", "vodafone_cash"),
        TestCash("ticket_test_cash", "CONFIRMED", "test_cash"),

        /** Released before paying, opened later by its reference (a notification). */
        Cancelled("ticket_cancelled", "CANCELLED", null),
        CancelledWithHost("ticket_cancelled_with_host", "CANCELLED", "card"),

        // MV-M6: opened from a My trips row.
        DetailsUpcoming("ticket_details_upcoming", "CONFIRMED", "card", TicketEntry.Details),
        DetailsPastRate("ticket_details_past_rate", "COMPLETED", "vodafone_cash", TicketEntry.Details, canRate = true),
        DetailsCancelled("ticket_details_cancelled", "CANCELLED", "card", TicketEntry.Details),
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
        setScreen(language, theme, viewModel, case.entry, case.canRate)
        if (case.status == "CONFIRMED") {
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
        entry: TicketEntry = TicketEntry.Paid,
        canRate: Boolean = false,
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
                            BookingConfirmedScreen(
                                ref = REF,
                                actions = TicketCallbacks(),
                                entry = entry,
                                canRate = canRate,
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val REF = "BRL-7K4M2X9P"
        const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L
        const val NOT_FOUND_STATUS = 404
        const val WHERE_EN =
            "Abdel Moneim Riad terminal, the bus bay behind the Ramses Hilton. The bus is white with a red stripe and a " +
                "paper sign: البرلس."
        const val WHERE_AR = "موقف عبد المنعم رياض، رصيف الأتوبيسات خلف رمسيس هيلتون. الأتوبيس أبيض بخط أحمر وعليه ورقة مكتوب فيها: البرلس."
    }
}
