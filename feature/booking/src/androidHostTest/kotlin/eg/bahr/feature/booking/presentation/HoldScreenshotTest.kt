package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Held seats × {ar, en} × {loading, loaded, checking at 00:00, the leave sheet}, through the real view
 * model on a fake repository and a clock that only moves when the test moves it, so the countdown in
 * each golden is fixed.
 *
 * - loading: the re-read has not answered; the countdown and total (from the route) draw, the summary
 *   is a skeleton.
 * - loaded: the server answered 8 seconds after the hold (14:52, the handoff's value).
 * - checking: the deadline passed and the re-read failed: 00:00 in error colour, "trying again".
 * - leave: Back asked "Leave and release your seats?".
 *
 * Record: `./gradlew :feature:booking:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
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

    @Test
    fun alreadyHeldArabic() = snapAlreadyHeld(AppLanguage.ARABIC)

    @Test
    fun alreadyHeldEnglish() = snapAlreadyHeld(AppLanguage.ENGLISH)

    /** Multi-day (M4-B0b, M4-M1a review #7): the summary names the day it is back. */
    @Test
    fun multiDayArabic() = snapMultiDay(AppLanguage.ARABIC)

    @Test
    fun multiDayEnglish() = snapMultiDay(AppLanguage.ENGLISH)

    private fun snapMultiDay(language: AppLanguage) {
        show(language, HoldViewModel(hold, answering(language, returnDate = SUNDAY), FakeActiveHoldStore(), clock))
        capture("hold_multi_day", language)
    }

    /** Continue elsewhere turned back to this live hold (M2-M2 guard, M2-M4 stored-hold guard). */
    private fun snapAlreadyHeld(language: AppLanguage) {
        show(language, HoldViewModel(hold, answering(language), FakeActiveHoldStore(), clock), alreadyHeldNotice = true)
        capture("hold_already_held", language)
    }

    /** The summary's placeholders in dark mode (M3-M14). */
    @Test
    fun loadingDarkArabic() = snapLoading(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun loadingDarkEnglish() = snapLoading(AppLanguage.ENGLISH, BahrThemeName.Dark)

    private fun snapLoading(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        show(language, HoldViewModel(hold, neverAnswers(), FakeActiveHoldStore(), clock), theme = theme)
        capture(if (theme == BahrThemeName.Dark) "hold_loading_dark" else "hold_loading", language)
    }

    private fun snapLoaded(language: AppLanguage) {
        show(language, HoldViewModel(hold, answering(language), FakeActiveHoldStore(), clock))
        capture("hold_loaded", language)
    }

    private fun snapChecking(language: AppLanguage) {
        val vm = HoldViewModel(hold, offline(), FakeActiveHoldStore(), clock)
        // The phone was away past the deadline; on return the re-read cannot get through.
        now += 16.minutes
        show(language, vm)
        capture("hold_checking", language)
    }

    private fun snapLeave(language: AppLanguage) {
        val vm = HoldViewModel(hold, answering(language), FakeActiveHoldStore(), clock)
        show(language, vm)
        compose.runOnUiThread { vm.requestLeave() }
        compose.waitForIdle()
        // The leave confirm is a bottom sheet since M3-M0b: let it slide fully up before the shot.
        repeat(SHEET_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        compose.onNodeWithTag(LEAVE_STAY_TAG, useUnmergedTree = true).assertExists()
        // The sheet's own window does not reach Robolectric's capture: the shot draws its inside, over
        // the dimmed page, as the other sheets' goldens do.
        val state = vm.uiState.value
        compose.runOnUiThread { vm.dismissLeave() }
        sheetShot.value = { LeaveSheetContent(state) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.captureScreenshot("hold_leave_${language.tag}")
    }

    /**
     * M3-M0b review R4-2: while the release is under way the sheet cannot be swiped away (Material hides
     * a sheet before it asks the screen), so a failure that comes back is shown in it, with "Leave anyway".
     */
    @Test
    fun `while releasing a swipe keeps the leave sheet - and a failure then offers to leave anyway`() {
        val answer = CompletableDeferred<AppResult<Unit>>()
        val repository = answering(AppLanguage.ENGLISH).apply { releaseHold = { _, _ -> answer.await() } }
        val vm = HoldViewModel(hold, repository, FakeActiveHoldStore(), clock)
        show(AppLanguage.ENGLISH, vm)
        compose.runOnUiThread { vm.requestLeave() }
        frames()
        compose.onNodeWithTag(LEAVE_CONFIRM_TAG, useUnmergedTree = true).performClick()
        frames()
        assertTrue(vm.uiState.value.isReleasing)

        compose.onNodeWithText("Leave and release your seats?", useUnmergedTree = true).performTouchInput { swipeDown() }
        frames()
        compose.onNodeWithTag(LEAVE_STAY_TAG, useUnmergedTree = true).assertIsDisplayed()

        answer.complete(AppResult.Failure(AppError.Network))
        frames()
        compose.onNodeWithText("Leave anyway", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(vm.uiState.value.isLeaveConfirmVisible)
    }

    private fun frames() {
        compose.waitForIdle()
        repeat(SHEET_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    /** The re-read never answers (a slow network): the summary waits, the countdown does not. */
    private fun neverAnswers() = FakeBookingRepository(heldBooking = { _, _ -> awaitCancellation() })

    /** The re-read cannot get through. */
    private fun offline() = FakeBookingRepository(heldBooking = { _, _ -> AppResult.Failure(AppError.Network) })

    /** The re-read, answered 8 seconds after the hold was placed. */
    private fun answering(
        language: AppLanguage,
        returnDate: LocalDate? = null,
    ): FakeBookingRepository {
        val arabic = language == AppLanguage.ARABIC
        val booking =
            BookingFixtures.heldBooking(
                serverNow = (Instant.parse("2026-10-09T19:27:30Z") + 8.seconds).toString(),
                title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                dayLabel = if (arabic) "السبت 10 أكتوبر" else "Sat 10 Oct",
                city = if (arabic) "القاهرة" else "Cairo",
                placeName = if (arabic) "عبد المنعم رياض" else "Abdel Moneim Riad",
                returnDate = returnDate,
            )
        return FakeBookingRepository(heldBooking = { _, _ -> AppResult.Success(booking) })
    }

    /** A sheet's inside drawn over the screen, as the sheet would show it (see [snapLeave]). */
    private val sheetShot = mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun show(
        language: AppLanguage,
        viewModel: HoldViewModel,
        alreadyHeldNotice: Boolean = false,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English, theme = theme) {
                    // The app's tab Scaffold sets onBackground as the content colour; a bare test root does not, so dark
                    // shots would draw uncoloured text black on the dark page (light goldens keep their recorded black).
                    CompositionLocalProvider(
                        LocalContentColor provides
                            if (theme == BahrThemeName.Dark) MaterialTheme.colorScheme.onBackground else LocalContentColor.current,
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            HoldScreen(hold = hold, onEnded = {}, alreadyHeldNotice = alreadyHeldNotice, viewModel = viewModel)
                            sheetShot.value?.let { sheet ->
                                Box(Modifier.fillMaxSize().background(BahrTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
                                    Box(
                                        Modifier
                                            .clip(
                                                BahrTheme.shapes.sheet,
                                            ).background(MaterialTheme.colorScheme.surface)
                                            .padding(top = BahrSpacing.xl),
                                    ) { sheet() }
                                }
                            }
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
        const val SHEET_FRAMES = 60
        const val SETTLE_MS = 1_000L

        /** The day after the held Saturday, 10 Oct: back from a one-night trip. */
        val SUNDAY = LocalDate(2026, 10, 11)
    }
}
