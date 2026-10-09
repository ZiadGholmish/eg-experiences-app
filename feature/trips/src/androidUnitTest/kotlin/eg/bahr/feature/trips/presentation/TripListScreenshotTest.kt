package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.model.BadgeDto
import eg.bahr.feature.trips.model.NextDepartureDto
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

/**
 * TripListScreen × {ar, en} × {loading, loaded, error, empty}, driven through the real view model with a
 * fake repository. The device is English (`en` qualifier) on purpose: the Arabic shots must still
 * be Arabic strings in RTL, which is the first-launch locale bug from M0-M3.
 *
 * Record: `./gradlew :feature:trips:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class TripListScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        // The loading spinner animates forever; a paused clock makes its frame deterministic.
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    // One page per language, because the server answers in the request's `Accept-Language`. The
    // four-digit price comes first: its leading digit is the one the card corner used to clip.
    @Test
    fun loadedArabic() =
        snapEach("trip_list_loaded", AppLanguage.ARABIC) {
            listTrips = {
                loadedPage(
                    dawn = "الفجر على بحيرة البرلس" to "الأكثر حجزًا",
                    kayak = "قنوات قطّاعي البوص بالكياك" to "موسم الفلامنجو",
                )
            }
        }

    @Test
    fun loadedEnglish() =
        snapEach("trip_list_loaded", AppLanguage.ENGLISH) {
            listTrips = {
                loadedPage(
                    dawn = "Dawn on Lake Burullus" to "Most booked",
                    kayak = "Reed-cutters' channels, by kayak" to "Flamingo season",
                )
            }
        }

    /** Two seeded trips: one with seats, one sold out. Each pair is (title, badge). */
    private fun loadedPage(
        dawn: Pair<String, String>,
        kayak: Pair<String, String>,
    ) = page(
        listOf(
            trip(
                1,
                title = dawn.first,
                priceEgp = 1450,
                badge = BadgeDto(label = dawn.second, tone = "primary"),
                nextDeparture = NextDepartureDto(SATURDAY, seatsRemaining = 6, capacity = 18, soldOut = false),
            ),
            trip(
                2,
                title = kayak.first,
                priceEgp = 520,
                durationLabel = "05:00 → 19:00",
                badge = BadgeDto(label = kayak.second, tone = "secondary"),
                nextDeparture = NextDepartureDto(SATURDAY, seatsRemaining = 0, capacity = 18, soldOut = true),
            ),
        ),
    )

    @Test
    fun loading() = snapEach("trip_list_loading") { listTrips = { awaitCancellation() } }

    @Test
    fun error() = snapEach("trip_list_error") { listTrips = { AppResult.Failure(AppError.Network) } }

    @Test
    fun empty() = snapEach("trip_list_empty") { listTrips = { page(emptyList()) } }

    private fun snapEach(
        prefix: String,
        vararg languages: AppLanguage = AppLanguage.entries.toTypedArray(),
        stub: FakeTripRepository.() -> Unit,
    ) {
        val viewModel = TripListViewModel(FakeTripRepository().apply(stub), AppErrorController())
        var language by mutableStateOf(languages.first())
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        TripListScreen(onTripClick = {}, viewModel = viewModel)
                    }
                }
            }
        }
        languages.forEach { shotLanguage ->
            compose.runOnUiThread { language = shotLanguage }
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.captureScreenshot("${prefix}_${shotLanguage.tag}")
        }
    }

    private companion object {
        val SATURDAY = LocalDate(2026, 10, 10)
    }
}
