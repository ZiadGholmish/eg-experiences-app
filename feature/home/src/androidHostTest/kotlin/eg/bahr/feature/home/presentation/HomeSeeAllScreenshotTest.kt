package eg.bahr.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.home.data.FakeTripRepository
import eg.bahr.feature.home.data.TripFixtures.page
import eg.bahr.feature.home.data.TripFixtures.trip
import eg.bahr.feature.home.data.waitlistMemory
import eg.bahr.feature.home.model.HomeDto
import eg.bahr.feature.home.model.HomeSeeAllDto
import eg.bahr.feature.home.model.TripsSectionDto
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

/**
 * M4-M1b: Home's row with its "See all" link × {ar, en}, through the real view model on a fake
 * repository. Moved from feature:trips' `TripListsScreenshotTest` with Home (M4-M1c), plumbing and all,
 * so the goldens are the same files. Device English on purpose (see TripListScreenshotTest).
 *
 * Record: `./gradlew :feature:home:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class HomeSeeAllScreenshotTest {
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

    // ---------- Home: a row with "See all" ----------

    /** The row holds 2 of 14 trips, so its title line carries "See all" (rows are up to 50 long now). */
    @Test
    @Config(qualifiers = "en-w360dp-h640dp-xhdpi")
    fun homeSeeAllArabic() = homeSeeAll(AppLanguage.ARABIC)

    @Test
    @Config(qualifiers = "en-w360dp-h640dp-xhdpi")
    fun homeSeeAllEnglish() = homeSeeAll(AppLanguage.ENGLISH)

    private fun homeSeeAll(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        val row =
            TripsSectionDto(
                id = "s-1",
                type = "trips",
                title = if (arabic) "مختارات" else "Featured trips",
                layout = "row",
                items = cards(arabic),
                totalItems = 14,
                seeAll = HomeSeeAllDto(type = "section", value = "s-1"),
            )
        val repository = FakeTripRepository(listTrips = { page(cards(arabic)) }, home = { AppResult.Success(HomeDto(listOf(row))) })
        val vm = TripListViewModel(repository, waitlistMemory())
        snap("home_see_all", language) { TripListScreen(onTripClick = {}, onMap = {}, onAlerts = {}, viewModel = vm) }
    }

    // ---------- Fixtures and plumbing ----------

    private fun cards(arabic: Boolean) =
        listOf(
            trip(
                1,
                title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                badge = BadgeDto(label = if (arabic) "الأكثر حجزًا" else "Most booked", tone = "primary"),
                nextDeparture = NextDepartureDto(FRIDAY, seatsRemaining = 6, capacity = 18, soldOut = false),
            ),
            trip(
                2,
                title = if (arabic) "قنوات قطّاعي البوص بالكياك" else "Reed-cutters' channels, by kayak",
                priceEgp = 520,
                durationLabel = "05:00 → 19:00",
                nextDeparture = NextDepartureDto(SATURDAY, seatsRemaining = 2, capacity = 12, soldOut = false),
            ),
        )

    /**
     * One screen, shot in each of [languages] in turn (the view model and its answers stay). [act],
     * when given, runs once the first screen has settled (a tap's effect on the view model), and the
     * clock then runs through the longest animation so the shot is the state it settles in.
     */
    private fun snap(
        prefix: String,
        vararg languages: AppLanguage,
        act: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        var language by mutableStateOf(languages.first())
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        languages.forEach { shotLanguage ->
            compose.runOnUiThread { language = shotLanguage }
            compose.waitForIdle()
            // A language switch re-keys the screen and its Paging presenter starts over: cached cards
            // come back at once, a cached error a pass later. Two frames let either land.
            repeat(SETTLE_FRAMES) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            // M4-M6: cards fade and slide in; run the clock through the longest animation so the shot is
            // the state the screen settles in, not a frame of the fade.
            settleAnimations()
            act?.let {
                compose.runOnIdle(it)
                settleAnimations()
            }
            compose.captureScreenshot("${prefix}_${shotLanguage.tag}")
        }
    }

    /**
     * With the clock paused, state written by an effect (the paging presenter's) is drawn only on a
     * later frame: frames first, the longest animation, then frames again for what that started.
     */
    private fun settleAnimations() {
        repeat(SETTLE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(BahrMotion.Long.toLong())
        compose.waitForIdle()
        repeat(SETTLE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 2

        val FRIDAY = LocalDate(2026, 10, 16)
        val SATURDAY = LocalDate(2026, 10, 17)
    }
}
