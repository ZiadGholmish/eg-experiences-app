package eg.bahr.feature.map.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.map.data.FakeTripMapRepository
import eg.bahr.feature.map.data.MapFixtures
import eg.bahr.feature.map.model.TripMapDto
import eg.bahr.feature.map.presentation.components.TripMapCanvas
import kotlinx.coroutines.awaitCancellation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * M4-M2: the map screen × {ar, en} × {loading, legend, a pin's card with the drive, a sold-out
 * overnight pin's card, error, empty}, through the real view model on a fake repository.
 *
 * The map itself is [TripMapCanvas], the plain-ground fallback a build without a Maps key draws:
 * neither Google map (maps-compose's MapView, the iOS SDK's GMSMapView) can render under Robolectric.
 * So these goldens cover the chrome (bar, toggles, legend, card, note, states) and the pins' and
 * line's own pictures, not Google's tiles, style or camera.
 *
 * Record: `./gradlew :feature:map:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class TripMapScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun pauseClock() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun loadingArabic() = loading(AppLanguage.ARABIC)

    @Test
    fun loadingEnglish() = loading(AppLanguage.ENGLISH)

    private fun loading(language: AppLanguage) {
        val vm = TripMapViewModel(FakeTripMapRepository(tripMap = { awaitCancellation() }))
        shoot("map_loading", language, vm)
    }

    /** Every pin framed, the legend with "Sold out" and "Bus departure", the toggles, the note. */
    @Test
    fun loadedArabic() = loaded(AppLanguage.ARABIC)

    @Test
    fun loadedEnglish() = loaded(AppLanguage.ENGLISH)

    private fun loaded(language: AppLanguage) = shoot("map_loaded", language, viewModel(language))

    /** A pin tapped with the drive on: its card, the dark Cairo marker, the dashed line and "150 km". */
    @Test
    fun driveArabic() = drive(AppLanguage.ARABIC)

    @Test
    fun driveEnglish() = drive(AppLanguage.ENGLISH)

    private fun drive(language: AppLanguage) {
        val vm = viewModel(language)
        shoot("map_drive", language, vm) {
            vm.selectPin("burullus-dawn")
            vm.toggleDrive()
        }
    }

    /** The sold-out two-day pin's card: "Sold out", the nights badge, its own bus time. */
    @Test
    fun soldOutArabic() = soldOut(AppLanguage.ARABIC)

    @Test
    fun soldOutEnglish() = soldOut(AppLanguage.ENGLISH)

    private fun soldOut(language: AppLanguage) {
        val vm = viewModel(language)
        shoot("map_sold_out", language, vm) { vm.selectPin("white-desert-overnight") }
    }

    @Test
    fun errorArabic() = error(AppLanguage.ARABIC)

    @Test
    fun errorEnglish() = error(AppLanguage.ENGLISH)

    private fun error(language: AppLanguage) {
        val vm = TripMapViewModel(FakeTripMapRepository(tripMap = { AppResult.Failure(AppError.Network) }))
        shoot("map_error", language, vm)
    }

    @Test
    fun emptyArabic() = empty(AppLanguage.ARABIC)

    @Test
    fun emptyEnglish() = empty(AppLanguage.ENGLISH)

    private fun empty(language: AppLanguage) {
        val vm = TripMapViewModel(FakeTripMapRepository(tripMap = { AppResult.Success(TripMapDto()) }))
        shoot("map_empty", language, vm)
    }

    /** Not a golden: a tap on a pin opens its card, and the card's "View trip" opens the trip. */
    @Test
    fun tappingAPinThenItsCardOpensTheTrip() {
        val vm = viewModel(AppLanguage.ENGLISH)
        val opened = mutableListOf<String>()
        setContent(AppLanguage.ENGLISH) { TripMapScreen(onBack = {}, onTripClick = { opened += it }, viewModel = vm, map = stubMap) }
        settle()

        // The pin's own click action: at country zoom the Burullus pins overlap, so a touch at its centre
        // could land on a neighbour drawn above it.
        compose.onNodeWithContentDescription("Dawn on Lake Burullus · 450 EGP").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals("burullus-dawn", vm.uiState.value.selectedSlug)
        compose.onNodeWithText("View trip").performClick()

        assertEquals(listOf("burullus-dawn"), opened)
    }

    // ---------- Plumbing ----------

    private fun viewModel(language: AppLanguage) =
        TripMapViewModel(FakeTripMapRepository(tripMap = { AppResult.Success(MapFixtures.map(arabic = language == AppLanguage.ARABIC)) }))

    private val stubMap: MapContent = { scene, onPin, onMap, padding, modifier -> TripMapCanvas(scene, onPin, onMap, padding, modifier) }

    /** [act] runs once the screen has settled (taps on the view model); the shot is what it settles in. */
    private fun shoot(
        prefix: String,
        language: AppLanguage,
        vm: TripMapViewModel,
        act: (() -> Unit)? = null,
    ) {
        setContent(language) { TripMapScreen(onBack = {}, onTripClick = {}, viewModel = vm, map = stubMap) }
        settle()
        act?.let {
            compose.runOnIdle(it)
            settle()
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
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
    }

    /**
     * With the clock paused: a few frames for the loaded state and the chrome's measured padding to
     * land, the longest animation (the legend/card crossfade), then frames again.
     */
    private fun settle() {
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
        const val SETTLE_FRAMES = 3
    }
}
