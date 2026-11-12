package eg.bahr.feature.host.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.FacetType
import eg.bahr.core.catalog.model.GovernorateChipDto
import eg.bahr.core.catalog.model.GovernorateDto
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.model.HostCardRatingDto
import eg.bahr.core.catalog.model.HostPageDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.host.data.FakeHostRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.test.assertEquals

/**
 * The hosts directory (MV-M1) × {ar, en} × {light, dark} × {loaded (the Hosts tab's root), filtered to nothing
 * (pushed from search, with its text and a region chosen), error}, through the real view model on a fake repository.
 * The server answers in the request's language (names, regions, kinds of trip), so each language gets its own copy.
 * No image URLs: a golden must not depend on Coil or a running MinIO.
 *
 * Record: `./gradlew :feature:host:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class HostsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        // Shimmers animate forever; a paused clock makes their frame deterministic.
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    /**
     * The tab's root, no Back: the count with "Sort: Top rated", the kinds of trip, the regions (a zero one dimmed),
     * individuals / companies, then a verified person with reviews, a company with none, a host with no region.
     */
    @Test
    fun loadedArabic() = loaded(AppLanguage.ARABIC)

    @Test
    fun loadedEnglish() = loaded(AppLanguage.ENGLISH)

    @Test
    fun loadedDarkArabic() = loaded(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun loadedDarkEnglish() = loaded(AppLanguage.ENGLISH, BahrThemeName.Dark)

    private fun loaded(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val repository =
            FakeHostRepository(
                listHosts = { _, _ -> page(cards(arabic), governorates(arabic)) },
                tripCategories = { AppResult.Success(categories(arabic)) },
            )
        snap("hosts_loaded", language, theme, HostsViewModel(null, null, repository), back = false)
    }

    /**
     * "See all hosts" from search with a region chosen, and nothing matches: "No hosts match this search.", the
     * chips still there to widen it, "Clear all" on.
     */
    @Test
    fun filteredEmptyArabic() = filteredEmpty(AppLanguage.ARABIC)

    @Test
    fun filteredEmptyEnglish() = filteredEmpty(AppLanguage.ENGLISH)

    @Test
    fun filteredEmptyDarkArabic() = filteredEmpty(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun filteredEmptyDarkEnglish() = filteredEmpty(AppLanguage.ENGLISH, BahrThemeName.Dark)

    private fun filteredEmpty(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val repository =
            FakeHostRepository(
                listHosts = { query, _ ->
                    val chips = governorates(arabic).map { it.copy(count = 0, selected = it.key == query.governorate) }
                    page(emptyList(), chips, totalPages = 0)
                },
                tripCategories = { AppResult.Success(categories(arabic)) },
            )
        val vm = HostsViewModel(if (arabic) "هدى" else "Hoda", null, repository)
        vm.selectGovernorate(RED_SEA)
        snap("hosts_filtered_empty", language, theme, vm, back = true)
        compose.onNodeWithTag(CLEAR_FILTERS_TAG).assertExists()
    }

    /** No connection: the error with its retry under the filters; the kinds of trip did not load either. */
    @Test
    fun errorArabic() = error(AppLanguage.ARABIC)

    @Test
    fun errorEnglish() = error(AppLanguage.ENGLISH)

    @Test
    fun errorDarkArabic() = error(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun errorDarkEnglish() = error(AppLanguage.ENGLISH, BahrThemeName.Dark)

    private fun error(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        val repository =
            FakeHostRepository(
                listHosts = { _, _ -> AppResult.Failure(AppError.Network) },
                tripCategories = { AppResult.Failure(AppError.Network) },
            )
        snap("hosts_error", language, theme, HostsViewModel(null, null, repository), back = true)
    }

    /** The sort menu: its button opens the three orders, and a pick is the list's new order. */
    @Test
    fun `the sort menu picks an order`() {
        val repository =
            FakeHostRepository(listHosts = { _, _ -> page(cards(arabic = false), governorates(arabic = false)) })
        val vm = HostsViewModel(null, null, repository)
        snap("hosts_loaded", AppLanguage.ENGLISH, BahrThemeName.LakeBurullus, vm, back = false, capture = false)

        // The menu opens in a popup on later frames: let the clock run on its own from here.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(SORT_BUTTON_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(sortOptionTag(HostSort.MostTrips)).performClick()
        compose.waitForIdle()

        assertEquals(HostSort.MostTrips, vm.uiState.value.sort)
    }

    /** MV-M1 review #1: a paste past the contract's 80 characters lands cut to 80, not dropped. */
    @Test
    fun `a long paste is cut to the limit`() {
        val repository = FakeHostRepository(listHosts = { _, _ -> page(cards(arabic = false), governorates(arabic = false)) })
        val vm = HostsViewModel(null, null, repository)
        snap("hosts_loaded", AppLanguage.ENGLISH, BahrThemeName.LakeBurullus, vm, back = false, capture = false)
        compose.mainClock.autoAdvance = true

        compose.onNodeWithTag(HOSTS_FIELD_TAG).performTextInput("a".repeat(HOSTS_MAX_QUERY_LENGTH + 5))
        compose.waitForIdle()

        compose.onNodeWithTag(HOSTS_FIELD_TAG).assertTextEquals("a".repeat(HOSTS_MAX_QUERY_LENGTH), includeEditableText = true)
        assertEquals("a".repeat(HOSTS_MAX_QUERY_LENGTH), vm.uiState.value.text)
    }

    // ---------- Fixtures and plumbing ----------

    private fun cards(arabic: Boolean) =
        listOf(
            HostCardDto(
                slug = "ashraf-el-bahr",
                kind = "person",
                name = if (arabic) "أشرف البحر" else "Ashraf El Bahr",
                verified = true,
                rating = HostCardRatingDto(4.7, 6),
                publishedTrips = 3,
                governorates = listOf(kafrElSheikh(arabic), GovernorateDto("beheira", if (arabic) "البحيرة" else "Beheira")),
            ),
            HostCardDto(
                slug = "red-sea-days",
                kind = "company",
                name = if (arabic) "ريد سي دايز" else "Red Sea Days",
                publishedTrips = 2,
                governorates = listOf(GovernorateDto(RED_SEA, if (arabic) "البحر الأحمر" else "Red Sea")),
            ),
            HostCardDto(
                slug = "rashid-heritage-trips",
                kind = "company",
                name = if (arabic) "رحلات تراث رشيد" else "Rashid Heritage Trips",
                verified = true,
                rating = HostCardRatingDto(4.0, 1),
                publishedTrips = 1,
            ),
        )

    private fun kafrElSheikh(arabic: Boolean) = GovernorateDto("kafr-el-sheikh", if (arabic) "كفر الشيخ" else "Kafr El Sheikh")

    private fun governorates(arabic: Boolean) =
        listOf(
            GovernorateChipDto("kafr-el-sheikh", if (arabic) "كفر الشيخ" else "Kafr El Sheikh", count = 4),
            GovernorateChipDto(RED_SEA, if (arabic) "البحر الأحمر" else "Red Sea", count = 2),
            GovernorateChipDto("beheira", if (arabic) "البحيرة" else "Beheira", count = 0),
        )

    private fun categories(arabic: Boolean) =
        listOf(
            FacetDto(FacetType.CATEGORY, "on_the_boat", if (arabic) "على المركب" else "On the boat", "sailing", "primary", 5),
            FacetDto(FacetType.CATEGORY, "birds", if (arabic) "طيور" else "Birds", "flutter_dash", "secondary", 3),
            FacetDto(FacetType.CATEGORY, "food", if (arabic) "أكل" else "Food", "restaurant", "tertiary", 2),
        )

    private fun page(
        items: List<HostCardDto>,
        governorates: List<GovernorateChipDto>,
        totalPages: Int = 1,
    ) = AppResult.Success(
        HostPageDto(
            items = items,
            size = items.size,
            totalItems = items.size.toLong(),
            totalPages = totalPages,
            governorates = governorates,
        ),
    )

    private fun snap(
        prefix: String,
        language: AppLanguage,
        theme: BahrThemeName,
        vm: HostsViewModel,
        back: Boolean,
        capture: Boolean = true,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English, theme = theme) {
                    // The app's tab Scaffold sets onBackground as the content colour; a bare test root does not.
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            HostsScreen(
                                initialQ = null,
                                initialSort = null,
                                onBack = if (back) ({}) else null,
                                onHostClick = { _, _, _ -> },
                                viewModel = vm,
                            )
                        }
                    }
                }
            }
        }
        // The paging presenter writes its state in an effect: frames, the longest animation, frames again.
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
        if (!capture) return
        val suffix = if (theme == BahrThemeName.Dark) "_dark" else ""
        compose.captureScreenshot("$prefix${suffix}_${language.tag}")
    }

    private companion object {
        const val SETTLE_FRAMES = 2
        const val RED_SEA = "red-sea"
    }
}
