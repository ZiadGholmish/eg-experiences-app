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
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.facets
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.sectionPage
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.BadgeDto
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.HomeSeeAllDto
import eg.bahr.feature.trips.model.NextDepartureDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripsSectionDto
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
 * M4-M1b: the category page × {ar, en} × {filtered, empty, loading, stale key}, a row's "See all"
 * list × {ar, en} × {loaded, gone}, and Home's row with its "See all" link, through the real view
 * models on a fake repository. The server answers in the request's language, so each language gets its
 * own copy. Device English on purpose (see TripListScreenshotTest).
 *
 * Record: `./gradlew :feature:trips:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class TripListsScreenshotTest {
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

    // ---------- Category page ----------

    /** "On the boat" with the Weekend chip on: the header tile, wrapped chips (one dimmed), two cards. */
    @Test
    fun categoryFilteredArabic() = category("category_filtered", AppLanguage.ARABIC, filter = "weekend")

    @Test
    fun categoryFilteredEnglish() = category("category_filtered", AppLanguage.ENGLISH, filter = "weekend")

    /** A filter nothing matches: the chips stay, the list says so. */
    @Test
    fun categoryEmptyArabic() = category("category_empty", AppLanguage.ARABIC, filter = "under_400", trips = { emptyList() })

    @Test
    fun categoryEmptyEnglish() = category("category_empty", AppLanguage.ENGLISH, filter = "under_400", trips = { emptyList() })

    /** Before the first page: the opener's label (a Home chip's, in the app's language), then a spinner. */
    @Test
    fun categoryLoadingArabic() = categoryLoading(AppLanguage.ARABIC, "في القارب")

    @Test
    fun categoryLoadingEnglish() = categoryLoading(AppLanguage.ENGLISH, "On the boat")

    private fun categoryLoading(
        language: AppLanguage,
        title: String,
    ) {
        val repository = FakeTripRepository(listFiltered = { awaitCancellation() })
        val vm = CategoryTripsViewModel("on_the_boat", title, repository, waitlistMemory())
        snap("category_loading", language) { CategoryTripsScreen("on_the_boat", title, {}, {}, viewModel = vm) }
    }

    /** A stale key (VALIDATION_FAILED): the page falls back to every trip, titled "All trips". */
    @Test
    fun categoryStaleKeyArabic() = categoryStaleKey(AppLanguage.ARABIC)

    @Test
    fun categoryStaleKeyEnglish() = categoryStaleKey(AppLanguage.ENGLISH)

    private fun categoryStaleKey(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        val refused = AppResult.Failure(AppError.Api("VALIDATION_FAILED", "Unknown category", 400))
        val repository =
            FakeTripRepository(
                // Every published trip: the All chip's count.
                listFiltered = { q ->
                    if (q.category !=
                        null
                    ) {
                        refused
                    } else {
                        page(cards(arabic), facets = facets(arabic = arabic), totalItems = 6)
                    }
                },
            )
        val vm = CategoryTripsViewModel("gone", null, repository, waitlistMemory())
        snap("category_stale_key", language) { CategoryTripsScreen("gone", null, {}, {}, viewModel = vm) }
    }

    private fun category(
        prefix: String,
        language: AppLanguage,
        filter: String?,
        trips: (Boolean) -> List<TripCardDto> = ::cards,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val repository =
            FakeTripRepository(
                listFiltered = { q ->
                    val chips = facets(category = q.category, filter = q.filter, arabic = arabic)
                    // The header's count is the selected filter chip's, as the server's would be.
                    val total = chips.first { it.type == "filter" && it.selected }.count ?: 0
                    page(trips(arabic), facets = chips, totalItems = total.toLong())
                },
            )
        val vm = CategoryTripsViewModel("on_the_boat", null, repository, waitlistMemory())
        filter?.let(vm::selectFilter)
        snap(prefix, language) { CategoryTripsScreen("on_the_boat", null, {}, {}, viewModel = vm) }
    }

    // ---------- A row's "See all" ----------

    @Test
    fun sectionLoadedArabic() = section("section_loaded", AppLanguage.ARABIC, "مختارات")

    @Test
    fun sectionLoadedEnglish() = section("section_loaded", AppLanguage.ENGLISH, "Featured trips")

    /** The section was switched off since Home was read: 404, the error state with its retry. */
    @Test
    fun sectionGone() {
        val notFound = AppResult.Failure(AppError.Api("NOT_FOUND", null, 404))
        val repository = FakeTripRepository(sectionTrips = { _, _ -> notFound })
        val vm = SectionTripsViewModel("s-1", "Featured trips", repository, waitlistMemory())
        snap("section_gone", AppLanguage.ARABIC, AppLanguage.ENGLISH) {
            SectionTripsScreen("s-1", "Featured trips", {}, {}, viewModel = vm)
        }
    }

    private fun section(
        prefix: String,
        language: AppLanguage,
        title: String,
    ) {
        val repository =
            FakeTripRepository(sectionTrips = {
                _,
                _,
                ->
                sectionPage(cards(language == AppLanguage.ARABIC), totalPages = 3, totalItems = 41)
            })
        val vm = SectionTripsViewModel("s-1", title, repository, waitlistMemory())
        snap(prefix, language) { SectionTripsScreen("s-1", title, {}, {}, viewModel = vm) }
    }

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
        snap("home_see_all", language) { TripListScreen(onTripClick = {}, viewModel = vm) }
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

    /** One screen, shot in each of [languages] in turn (the view model and its answers stay). */
    private fun snap(
        prefix: String,
        vararg languages: AppLanguage,
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
            compose.captureScreenshot("${prefix}_${shotLanguage.tag}")
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 2
        val FRIDAY = LocalDate(2026, 10, 16)
        val SATURDAY = LocalDate(2026, 10, 17)
    }
}
