package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.BookingFixtures.SLUG
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.model.BookingDepartureDto
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

/**
 * Date + party × {ar, en} × {loading, loaded, no seats}, driven through the real view model with a
 * fake repository; plus the held-seats placeholder.
 *
 * - loading: the bar, title and button are drawn while the trip loads.
 * - loaded: the date picked on the trip page selected, a party of 2 and the guest filled in, so the
 *   coral button is live.
 * - no seats: the hold came back `NO_SEATS_AVAILABLE` and the re-read dates show every D3 reason
 *   (cancelled, sold out, booking closed) next to the open ones; the error sits above the button.
 *
 * The form screens are shot on a tall screen so the whole form is in one golden.
 *
 * Record: `./gradlew :feature:booking:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
class BookingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Config(qualifiers = "en-w360dp-h640dp-xhdpi")
    @Test
    fun loadingArabic() = snapLoading(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h640dp-xhdpi")
    @Test
    fun loadingEnglish() = snapLoading(AppLanguage.ENGLISH)

    @Test
    fun loadedArabic() = snapLoaded(AppLanguage.ARABIC, "الفجر على بحيرة البرلس", "ندى حسن")

    @Test
    fun loadedEnglish() = snapLoaded(AppLanguage.ENGLISH, "Dawn on Lake Burullus", "Nada Hassan")

    @Test
    fun noSeatsArabic() = snapNoSeats(AppLanguage.ARABIC, "الفجر على بحيرة البرلس", "ندى حسن")

    @Test
    fun noSeatsEnglish() = snapNoSeats(AppLanguage.ENGLISH, "Dawn on Lake Burullus", "Nada Hassan")

    @Config(qualifiers = "en-w360dp-h640dp-xhdpi")
    @Test
    fun heldArabic() = snapHeld(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h640dp-xhdpi")
    @Test
    fun heldEnglish() = snapHeld(AppLanguage.ENGLISH)

    private fun snapLoading(language: AppLanguage) {
        val repo = FakeBookingRepository(tripBySlug = { awaitCancellation() }, departuresFor = { awaitCancellation() })
        show(language, BookingViewModel(SLUG, "dep-2", repo))
        capture("booking_loading", language)
    }

    private fun snapLoaded(
        language: AppLanguage,
        title: String,
        name: String,
    ) {
        val repo =
            FakeBookingRepository(
                tripBySlug = { AppResult.Success(BookingFixtures.trip(title = title)) },
                departuresFor = { AppResult.Success(BookingFixtures.saturdays()) },
            )
        val vm = BookingViewModel(SLUG, "dep-2", repo)
        show(language, vm)
        compose.runOnUiThread {
            vm.increaseParty()
            vm.setGuestName(name)
            vm.setGuestPhone("010 1234 5678")
        }
        capture("booking_loaded", language)
    }

    private fun snapNoSeats(
        language: AppLanguage,
        title: String,
        name: String,
    ) {
        var dates = BookingFixtures.saturdays()
        val repo =
            FakeBookingRepository(
                tripBySlug = { AppResult.Success(BookingFixtures.trip(title = title)) },
                departuresFor = { AppResult.Success(dates) },
                placeHold = { AppResult.Failure(AppError.Api(ApiErrorCodes.NO_SEATS_AVAILABLE, null, CONFLICT)) },
            )
        val vm = BookingViewModel(SLUG, "dep-2", repo)
        show(language, vm)
        dates = everyReason(dates)
        compose.runOnUiThread {
            vm.setGuestName(name)
            vm.setGuestPhone("010 1234 5678")
            vm.placeHold()
        }
        capture("booking_no_seats", language)
    }

    private fun snapHeld(language: AppLanguage) {
        val hold =
            HoldRoute(
                ref = "BRL-7K4M2X9P",
                holdExpiresAt = "2026-10-09T22:42:30+03:00",
                serverNow = "2026-10-09T22:27:30+03:00",
                totalAmount = 900,
                totalCurrency = "EGP",
                guestPhone = "01012345678",
            )
        setContent(language) { HoldScreen(hold = hold, onBack = {}) }
        capture("booking_held", language)
    }

    /** dep-1 cancelled (it was also full), dep-2 just sold out, dep-3 sold out, dep-4 booking closed. */
    private fun everyReason(dates: List<BookingDepartureDto>) =
        dates.map {
            when (it.id) {
                "dep-1" -> it.copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "CANCELLED")
                "dep-2" -> it.copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "SOLD_OUT")
                "dep-4" -> it.copy(bookable = false, unavailableReason = "CLOSED")
                else -> it
            }
        } + BookingFixtures.saturdays().first().copy(id = "dep-5", date = kotlinx.datetime.LocalDate(2026, 11, 7))

    private fun show(
        language: AppLanguage,
        viewModel: BookingViewModel,
    ) = setContent(language) {
        BookingScreen(slug = SLUG, departureId = "dep-2", onBack = {}, onHeld = {}, viewModel = viewModel)
    }

    private fun setContent(
        language: AppLanguage,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
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
        // Let colour animations (the button's fill) settle; the clock is paused.
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private companion object {
        const val SETTLE_MS = 1_000L
        const val CONFLICT = 409
    }
}
