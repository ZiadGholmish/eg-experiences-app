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
 * TripListScreen × {ar, en} × {loading, loaded, error}, driven through the real view model with a
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

    @Test
    fun loaded() =
        snapEach("trip_list_loaded") {
            listTrips = {
                page(
                    listOf(
                        trip(1, title = "فجر بحيرة البرلس", category = "طيور", priceEgp = 450),
                        trip(2, title = "Sunset sail on Lake Burullus", category = "إبحار", priceEgp = 1200),
                    ),
                )
            }
        }

    @Test
    fun loading() = snapEach("trip_list_loading") { listTrips = { awaitCancellation() } }

    @Test
    fun error() = snapEach("trip_list_error") { listTrips = { AppResult.Failure(AppError.Network) } }

    private fun snapEach(
        prefix: String,
        stub: FakeTripRepository.() -> Unit,
    ) {
        val viewModel = TripListViewModel(FakeTripRepository().apply(stub), AppErrorController())
        var language by mutableStateOf(AppLanguage.ARABIC)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        TripListScreen(onTripClick = {}, viewModel = viewModel)
                    }
                }
            }
        }
        AppLanguage.entries.forEach { shotLanguage ->
            compose.runOnUiThread { language = shotLanguage }
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.captureScreenshot("${prefix}_${shotLanguage.tag}")
        }
    }
}
