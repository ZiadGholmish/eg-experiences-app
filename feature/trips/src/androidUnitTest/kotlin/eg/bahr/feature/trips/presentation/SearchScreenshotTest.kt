package eg.bahr.feature.trips.presentation

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
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.trips.data.FakeRecentSearchesStore
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.facets
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
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
 * M4-M3: the search screen × {ar, en} × {recent searches, start hint, loading, results, no results, error}, through the
 * real view model on a fake repository. A search is started the way a recent-search tap starts one
 * (at once), not by typing: the clock is paused here, so a debounce would never end. Device English
 * on purpose (see TripListScreenshotTest).
 *
 * Record: `./gradlew :feature:trips:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SearchScreenshotTest {
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

    /** Nothing typed yet: the field focused, the recent searches (mixed languages) with "Clear all". */
    @Test
    fun recentsArabic() = recents(AppLanguage.ARABIC)

    @Test
    fun recentsEnglish() = recents(AppLanguage.ENGLISH)

    private fun recents(language: AppLanguage) {
        val store = FakeRecentSearchesStore(listOf("فلوكة", "birds", "بلطيم", "kayak"))
        val vm = SearchTripsViewModel(FakeTripRepository(), store, waitlistMemory())
        snap("search_recents", language, vm)
    }

    /** No recent searches yet: the line on what can be searched. */
    @Test
    fun startArabic() =
        snap("search_start", AppLanguage.ARABIC, SearchTripsViewModel(FakeTripRepository(), FakeRecentSearchesStore(), waitlistMemory()))

    @Test
    fun startEnglish() =
        snap("search_start", AppLanguage.ENGLISH, SearchTripsViewModel(FakeTripRepository(), FakeRecentSearchesStore(), waitlistMemory()))

    /** A search waiting for its first page: the spinner under the field, the text kept. */
    @Test
    fun loadingArabic() = loading(AppLanguage.ARABIC, "فلوكة")

    @Test
    fun loadingEnglish() = loading(AppLanguage.ENGLISH, "felucca")

    private fun loading(
        language: AppLanguage,
        text: String,
    ) {
        val vm =
            SearchTripsViewModel(FakeTripRepository(listFiltered = { awaitCancellation() }), FakeRecentSearchesStore(), waitlistMemory())
        vm.searchRecent(text)
        snap("search_loading", language, vm)
    }

    /** A search with results: the count, the filter chips with the search's counts, the cards best match first. */
    @Test
    fun resultsArabic() = results(AppLanguage.ARABIC, "البرلس")

    @Test
    fun resultsEnglish() = results(AppLanguage.ENGLISH, "Burullus")

    private fun results(
        language: AppLanguage,
        text: String,
    ) {
        val arabic = language == AppLanguage.ARABIC
        // The chips count only the matches, as the server's do: both cards, both on a weekend, one under 400.
        val counts = mapOf("all" to 2, "weekend" to 2, "under_400" to 1, "half_day" to 0)
        val chips = facets(arabic = arabic).map { it.copy(count = counts[it.key] ?: it.count) }
        val repository = FakeTripRepository(listFiltered = { page(cards(arabic), facets = chips, totalItems = 2) })
        val vm = SearchTripsViewModel(repository, FakeRecentSearchesStore(), waitlistMemory())
        vm.searchRecent(text)
        snap("search_results", language, vm)
    }

    /** Nothing matches: the chips at zero (dimmed) and the sentence naming what was typed. */
    @Test
    fun noResultsArabic() = noResults(AppLanguage.ARABIC, "غطس")

    @Test
    fun noResultsEnglish() = noResults(AppLanguage.ENGLISH, "scuba")

    private fun noResults(
        language: AppLanguage,
        text: String,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val zero = facets(arabic = arabic).map { it.copy(count = 0) }
        val repository = FakeTripRepository(listFiltered = { page(emptyList(), facets = zero, totalItems = 0, totalPages = 0) })
        val vm = SearchTripsViewModel(repository, FakeRecentSearchesStore(), waitlistMemory())
        vm.searchRecent(text)
        snap("search_no_results", language, vm)
    }

    /** The search failed (no connection): the error with its retry, the text still in the field. */
    @Test
    fun errorArabic() = error(AppLanguage.ARABIC, "فلوكة")

    @Test
    fun errorEnglish() = error(AppLanguage.ENGLISH, "felucca")

    private fun error(
        language: AppLanguage,
        text: String,
    ) {
        val repository = FakeTripRepository(listFiltered = { AppResult.Failure(AppError.Network) })
        val vm = SearchTripsViewModel(repository, FakeRecentSearchesStore(), waitlistMemory())
        vm.searchRecent(text)
        snap("search_error", language, vm)
    }

    // ---------- Fixtures and plumbing ----------

    private fun cards(arabic: Boolean) =
        listOf(
            trip(
                1,
                title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                nextDeparture = NextDepartureDto(FRIDAY, seatsRemaining = 6, capacity = 18, soldOut = false),
            ),
            trip(
                2,
                title = if (arabic) "جداريات برج البرلس على رجليك" else "Murals of Burg El Burullus, on foot",
                priceEgp = 220,
                durationLabel = "07:00 → 16:00",
                nextDeparture = NextDepartureDto(SATURDAY, seatsRemaining = 2, capacity = 12, soldOut = false),
            ),
        )

    private fun snap(
        prefix: String,
        language: AppLanguage,
        vm: SearchTripsViewModel,
    ) = shoot(prefix, language) { SearchTripsScreen(onBack = {}, onTripClick = {}, viewModel = vm) }

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        content: @Composable () -> Unit,
    ) {
        var current by mutableStateOf(language)
        compose.setContent {
            ProvideAppLanguage(current) {
                BahrTheme(locale = if (current == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.waitForIdle()
        // The paged list fills in after its first frame (the presenter collects in an effect); a
        // couple of frames let the cards, the empty line or the error land.
        repeat(SETTLE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private companion object {
        const val SETTLE_FRAMES = 2
        val FRIDAY = LocalDate(2026, 10, 16)
        val SATURDAY = LocalDate(2026, 10, 17)
    }
}
