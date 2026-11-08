package eg.bahr.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import eg.bahr.core.catalog.model.HomeCategoryDto
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.presentation.WaitlistTags
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.BahrSkeleton
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.home.data.TripFixtures.trip
import eg.bahr.feature.home.model.BannersSectionDto
import eg.bahr.feature.home.model.CategoriesSectionDto
import eg.bahr.feature.home.model.DeparturesSectionDto
import eg.bahr.feature.home.model.HomeActionDto
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.model.HomeDepartureDto
import eg.bahr.feature.home.model.HomeHostDto
import eg.bahr.feature.home.model.HomeHostRatingDto
import eg.bahr.feature.home.model.HomeNoteDto
import eg.bahr.feature.home.model.HomeReviewDto
import eg.bahr.feature.home.model.HomeSectionDto
import eg.bahr.feature.home.model.HomeTripRefDto
import eg.bahr.feature.home.model.HostsSectionDto
import eg.bahr.feature.home.model.NoteSectionDto
import eg.bahr.feature.home.model.ReviewsSectionDto
import eg.bahr.feature.home.model.TripsSectionDto
import eg.bahr.feature.home.presentation.components.HomeLayoutSkeleton
import eg.bahr.feature.home.presentation.components.HomeSection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * MH-M1: one shot per Home layout × {ar, en} × {light, dark}, each drawn through the parser and the registry as
 * Home draws it. The card layouts carry the three new cards (a rail of hosts, a grid of departures, a list of
 * reviews), plus their other arrangements (departures and reviews in a rail, hosts in a list); trip cards are
 * in TripListScreenshotTest. Images do not load on the JVM, so every picture is its ground: what a slow network
 * shows before the photo. Then every layout's skeleton, stacked.
 *
 * Record: `./gradlew :feature:home:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h560dp-xhdpi")
class HomeLayoutsScreenshotTest {
    private companion object {
        const val CONTENT_TAG = "home_layout_content"

        /** Room for a grid's or a list's every row. */
        const val TALL = "en-w360dp-h1100dp-xhdpi"
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hero() = sectionShots("hero") { banners(it, "hero", count = 3) }

    @Test
    fun strip() = sectionShots("strip") { banners(it, "strip", count = 2) }

    @Test
    fun posterRail() = sectionShots("poster_rail") { banners(it, "poster_rail") }

    @Test
    fun tileRail() = sectionShots("tile_rail") { banners(it, "tile_rail") }

    @Test
    fun tileGrid() = sectionShots("tile_grid") { banners(it, "tile_grid", columns = 3) }

    @Test
    fun circles() = sectionShots("circles") { banners(it, "circles") }

    @Test
    fun cardRailHosts() = sectionShots("card_rail_hosts") { hosts(it, "card_rail") }

    @Test
    fun cardRailDepartures() = sectionShots("card_rail_departures") { departures(it, "card_rail") }

    @Test
    fun cardRailReviews() = sectionShots("card_rail_reviews") { reviews(it, "card_rail") }

    @Test
    @Config(qualifiers = TALL)
    fun cardGridDepartures() = sectionShots("card_grid_departures") { departures(it, "card_grid") }

    @Test
    @Config(qualifiers = TALL)
    fun cardListReviews() = sectionShots("card_list_reviews") { reviews(it, "card_list") }

    @Test
    @Config(qualifiers = TALL)
    fun cardListHosts() = sectionShots("card_list_hosts") { hosts(it, "card_list") }

    @Test
    @Config(qualifiers = TALL)
    fun cardGridTrips() = sectionShots("card_grid_trips") { trips(it, "card_grid") }

    @Test
    @Config(qualifiers = TALL)
    fun cardListTrips() = sectionShots("card_list_trips") { trips(it, "card_list") }

    @Test
    @Config(qualifiers = TALL)
    fun cardGridHosts() = sectionShots("card_grid_hosts") { hosts(it, "card_grid") }

    @Test
    fun chips() = sectionShots("chips") { categories(it) }

    @Test
    fun noteCard() = sectionShots("note_card") { note(it) }

    /** The image layouts' skeletons, stacked (hero to circles). */
    @Test
    @Config(qualifiers = "en-w360dp-h1300dp-xhdpi")
    fun imageSkeletons() = skeletonShots("skeletons_images", HomeLayout.entries.filter { it.shape == HomeShape.Assets })

    /** The other layouts' skeletons, stacked (cards, chips, note). */
    @Test
    @Config(qualifiers = "en-w360dp-h1900dp-xhdpi")
    fun cardSkeletons() = skeletonShots("skeletons_cards", HomeLayout.entries.filter { it.shape != HomeShape.Assets })

    private fun skeletonShots(
        name: String,
        layouts: List<HomeLayout>,
    ) = shots(name) {
        BahrSkeleton(verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl)) {
            layouts.forEach { layout -> HomeLayoutSkeleton(layout) }
        }
    }

    private fun sectionShots(
        name: String,
        section: (arabic: Boolean) -> HomeSectionDto,
    ) = shots(name) { arabic ->
        val block = parseHomeSections(listOf(section(arabic))) { error(it) }.single()
        HomeSection(
            block,
            perPersonLabel = if (arabic) "للفرد" else "per person",
            waitlistTags = WaitlistTags.None,
            onTripClick = {},
            onAction = {},
        )
    }

    private fun shots(
        name: String,
        content: @Composable (arabic: Boolean) -> Unit,
    ) {
        var language by mutableStateOf(AppLanguage.ARABIC)
        var theme by mutableStateOf(BahrThemeName.LakeBurullus)
        compose.setContent {
            ProvideAppLanguage(language) {
                val arabic = language == AppLanguage.ARABIC
                BahrTheme(locale = if (arabic) BahrLocale.Arabic else BahrLocale.English, theme = theme) {
                    // The app's tab Scaffold sets onBackground as the content colour; a bare test root does not.
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            // Measured at its own height, never squeezed to the screen's (MH-M1 review #2): as in
                            // Home's LazyColumn, where a section's height is unbounded. fitsOnScreen() then proves
                            // the shot shows all of it.
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(Alignment.Top, unbounded = true)
                                    .testTag(CONTENT_TAG)
                                    .padding(vertical = BahrSpacing.lg),
                            ) {
                                content(arabic)
                            }
                        }
                    }
                }
            }
        }
        // The skeleton's shimmer and the hero's timer must not move between shots.
        compose.mainClock.autoAdvance = false
        listOf(BahrThemeName.LakeBurullus, BahrThemeName.Dark).forEach { shotTheme ->
            AppLanguage.entries.forEach { shotLanguage ->
                compose.runOnUiThread {
                    language = shotLanguage
                    theme = shotTheme
                }
                compose.waitForIdle()
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val dark = if (shotTheme == BahrThemeName.Dark) "_dark" else ""
                fitsOnScreen("$name ${shotLanguage.tag}$dark")
                compose.captureScreenshot("home_layout_${name}_${shotLanguage.tag}$dark")
            }
        }
    }

    /** Fails a shot that would cut its content off at the bottom: the golden must show every row. */
    private fun fitsOnScreen(shot: String) {
        val content =
            compose
                .onNodeWithTag(CONTENT_TAG)
                .fetchSemanticsNode()
                .size.height
        val screen =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .size.height
        assertTrue(content <= screen, "$shot: content is $content px tall, the screen $screen px; raise the test's height")
    }

    private fun t(
        arabic: Boolean,
        ar: String,
        en: String,
    ) = if (arabic) ar else en

    private val photo = ImageDto(url = "https://cdn.invalid/photo.jpg", width = 1200, height = 600)

    private fun banners(
        arabic: Boolean,
        layout: String,
        count: Int = 5,
        columns: Int? = null,
    ): BannersSectionDto {
        val titles =
            listOf(
                t(arabic, "الفجر على البحيرة", "Dawn on the lake"),
                t(arabic, "الفلامنجو", "Flamingos"),
                t(arabic, "الصحرا البيضا", "White Desert"),
                t(arabic, "ركوب الكياك", "Kayaking"),
                t(arabic, "رحلات بالليل", "Night trips"),
            )
        return BannersSectionDto(
            id = "banners",
            type = "banners",
            title = if (layout == "hero") null else t(arabic, "اكتشف", "Discover"),
            layout = layout,
            columns = columns,
            items = (0 until count).map { HomeBannerDto("b$it", titles[it], photo, HomeActionDto("trip", "trip-$it")) },
        )
    }

    private fun hosts(
        arabic: Boolean,
        layout: String,
    ) = HostsSectionDto(
        id = "hosts",
        type = "hosts",
        title = t(arabic, "مضيفين مميزين", "Featured hosts"),
        layout = layout,
        items =
            listOf(
                HomeHostDto(
                    "ashraf",
                    "person",
                    t(arabic, "أشرف البحر", "Ashraf El-Bahr"),
                    verified = true,
                    tripsRun = 42,
                    rating = HomeHostRatingDto(4.8, 37),
                ),
                HomeHostDto("boats", "company", t(arabic, "مراكب البرلس", "Burullus Boats"), verified = false, tripsRun = 7),
                HomeHostDto("mona", "person", "Mona S.", verified = true, tripsRun = 0, rating = HomeHostRatingDto(5.0, 2)),
            ),
    )

    private fun departures(
        arabic: Boolean,
        layout: String,
    ) = DeparturesSectionDto(
        id = "departures",
        type = "departures",
        title = t(arabic, "مسافرة قريب", "Leaving soon"),
        layout = layout,
        items =
            listOf(
                departure("d1", t(arabic, "الفجر على بحيرة البرلس", "Dawn on Lake Burullus"), LocalDate(2026, 11, 14), 3, 450),
                departure(
                    "d2",
                    t(arabic, "قنوات قطّاعي البوص بالكياك", "Reed-cutters' channels, by kayak"),
                    LocalDate(2026, 11, 15),
                    12,
                    1450,
                ),
                departure("d3", t(arabic, "الصحرا البيضا، ليلة", "The White Desert, overnight"), LocalDate(2026, 11, 20), 1, 3200),
            ),
    )

    private fun departure(
        id: String,
        title: String,
        date: LocalDate,
        seats: Int,
        price: Long,
    ) = HomeDepartureDto(id, date, LocalTime(5, 0), seats, MoneyDto(price, "EGP"), HomeTripRefDto("trip-$id", title, photo))

    private fun reviews(
        arabic: Boolean,
        layout: String,
    ) = ReviewsSectionDto(
        id = "reviews",
        type = "reviews",
        title = t(arabic, "المسافرين بيقولوا", "What travellers say"),
        layout = layout,
        items =
            listOf(
                HomeReviewDto(
                    "r1",
                    rating = 5,
                    author = t(arabic, "منى س.", "Mona S."),
                    tone = "secondary",
                    dateISO = LocalDate(2026, 10, 20),
                    verified = true,
                    body =
                        t(
                            arabic,
                            "الفلامنجو وقت الشروق يستاهل الصحيان بدري. المرشد كان فاهم وصبور، والفطار على المركب كان حلو جدًا.",
                            "The flamingos at sunrise were worth the 4 am start. The guide knew every bird, and breakfast on the boat was lovely.",
                        ),
                    trip = HomeTripRefDto("burullus-dawn", t(arabic, "الفجر على بحيرة البرلس", "Dawn on Lake Burullus"), photo),
                ),
                HomeReviewDto(
                    "r2",
                    rating = 4,
                    author = "Karim A.",
                    tone = "quaternary",
                    dateISO = LocalDate(2026, 10, 12),
                    verified = false,
                    body = null,
                    trip = HomeTripRefDto("white-desert", t(arabic, "الصحرا البيضا، ليلة", "The White Desert, overnight")),
                ),
            ),
    )

    /** Trip cards in a grid and a list (the rail is TripListScreenshotTest's): a four-digit price on a half-width card. */
    private fun trips(
        arabic: Boolean,
        layout: String,
    ) = TripsSectionDto(
        id = "trips",
        type = "trips",
        title = t(arabic, "رحلات مميزة", "Featured trips"),
        layout = layout,
        items =
            listOf(
                trip(1, title = t(arabic, "الفجر على بحيرة البرلس", "Dawn on Lake Burullus"), priceEgp = 1450),
                trip(2, title = t(arabic, "قنوات قطّاعي البوص بالكياك", "Reed-cutters' channels, by kayak"), priceEgp = 520),
            ),
    )

    private fun categories(arabic: Boolean) =
        CategoriesSectionDto(
            id = "kinds",
            type = "categories",
            title = t(arabic, "تصفح حسب النوع", "Browse by kind"),
            layout = "chips",
            items =
                listOf(
                    HomeCategoryDto("on_the_boat", t(arabic, "على المركب", "On the boat"), "sailing", "primary"),
                    HomeCategoryDto("birds", t(arabic, "طيور", "Birds"), "flutter_dash", "secondary"),
                    HomeCategoryDto("night_trips", t(arabic, "رحلات بالليل", "Night trips"), "bedtime", "quaternary"),
                ),
        )

    private fun note(arabic: Boolean) =
        NoteSectionDto(
            id = "note",
            type = "note",
            title = t(arabic, "قبل ما تحجز", "Before you book"),
            layout = "note_card",
            items =
                listOf(
                    HomeNoteDto(
                        t(
                            arabic,
                            "كل الرحلات بتتحرك من موقف عبد المنعم رياض في القاهرة، وميعاد الأتوبيس مكتوب في صفحة كل رحلة.",
                            "Every trip leaves from Abdel Moneim Riad in Cairo; each trip's page has its bus time.",
                        ),
                        "directions_bus",
                    ),
                ),
        )
}
