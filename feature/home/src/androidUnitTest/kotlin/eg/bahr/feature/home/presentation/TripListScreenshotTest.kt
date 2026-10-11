package eg.bahr.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToKey
import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.HomeCategoryDto
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.catalog.presentation.WaitlistTags
import eg.bahr.core.catalog.presentation.components.TripCard
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.components.BahrCard
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.home.data.FakeTripRepository
import eg.bahr.feature.home.data.TripFixtures.facets
import eg.bahr.feature.home.data.TripFixtures.page
import eg.bahr.feature.home.data.TripFixtures.trip
import eg.bahr.feature.home.data.waitlistMemory
import eg.bahr.feature.home.model.BannersSectionDto
import eg.bahr.feature.home.model.CategoriesSectionDto
import eg.bahr.feature.home.model.HomeActionDto
import eg.bahr.feature.home.model.HomeBannerDto
import eg.bahr.feature.home.model.HomeDto
import eg.bahr.feature.home.model.HomeNoteDto
import eg.bahr.feature.home.model.NoteSectionDto
import eg.bahr.feature.home.model.TripsSectionDto
import eg.bahr.feature.home.presentation.components.HomeSection
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
 * Record: `./gradlew :feature:home:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
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
        facets: List<FacetDto> = emptyList(),
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
        facets = facets,
    )

    @Test
    fun loading() = snapEach("trip_list_loading") { listTrips = { awaitCancellation() } }

    // The header slot (M2-M4: booking's "Continue your booking" card, filled by :composeApp). A
    // stand-in card here, since this feature may not import booking; the real card has its own
    // goldens in feature:booking. Shows where the slot sits: under the title in the list, above the
    // full-screen states. The goldens without a header above are the "slot draws nothing" case.
    @Test
    fun loadedWithHeaderArabic() =
        snapEach("trip_list_header_loaded", AppLanguage.ARABIC, header = { HeaderStandIn(it) }) {
            listTrips = {
                loadedPage(
                    dawn = "الفجر على بحيرة البرلس" to "الأكثر حجزًا",
                    kayak = "قنوات قطّاعي البوص بالكياك" to "موسم الفلامنجو",
                )
            }
        }

    @Test
    fun loadedWithHeaderEnglish() =
        snapEach("trip_list_header_loaded", AppLanguage.ENGLISH, header = { HeaderStandIn(it) }) {
            listTrips = {
                loadedPage(
                    dawn = "Dawn on Lake Burullus" to "Most booked",
                    kayak = "Reed-cutters' channels, by kayak" to "Flamingo season",
                )
            }
        }

    @Test
    fun errorWithHeader() =
        snapEach("trip_list_header_error", header = { HeaderStandIn(it) }, settle = true) {
            listTrips =
                { AppResult.Failure(AppError.Network) }
        }

    @Composable
    private fun HeaderStandIn(gap: Modifier) {
        BahrCard(modifier = gap.fillMaxWidth()) {
            Text("Header slot", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(BahrSpacing.lg))
        }
    }

    @Test
    fun error() = snapEach("trip_list_error", settle = true) { listTrips = { AppResult.Failure(AppError.Network) } }

    @Test
    fun empty() = snapEach("trip_list_empty") { listTrips = { page(emptyList()) } }

    // ---------- Home's server-driven sections (M4-M1a) ----------
    // A tall screen, so the banner carousel, a trip row, the category chips and the start of "All
    // trips" are all in one shot. The images are left to the ground (no network in a test).

    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun homeLoadedArabic() =
        snapEach("home_loaded", AppLanguage.ARABIC) {
            listTrips =
                {
                    loadedPage(
                        dawn = "الفجر على بحيرة البرلس" to "الأكثر حجزًا",
                        kayak = "قنوات قطّاعي البوص بالكياك" to "موسم الفلامنجو",
                        facets = facets(arabic = true),
                    )
                }
            home = { AppResult.Success(home(arabic = true)) }
        }

    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun homeLoadedEnglish() =
        snapEach("home_loaded", AppLanguage.ENGLISH) {
            listTrips =
                {
                    loadedPage(
                        dawn = "Dawn on Lake Burullus" to "Most booked",
                        kayak =
                            "Reed-cutters' channels, by kayak" to "Flamingo season",
                        facets = facets(),
                    )
                }
            home = { AppResult.Success(home(arabic = false)) }
        }

    // ---------- Home's filter chips (M4-M1) ----------

    /** "Weekend" tapped: its trips under the chips, the chip selected and the count. */
    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun homeFilteredArabic() = homeFiltered(AppLanguage.ARABIC)

    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun homeFilteredEnglish() = homeFiltered(AppLanguage.ENGLISH)

    private fun homeFiltered(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snapEach("home_filtered", language, act = { selectFilter("weekend") }) {
            listFiltered = { q ->
                val dawn = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus"
                page(
                    listOf(
                        trip(
                            1,
                            title = dawn,
                            nextDeparture = NextDepartureDto(SATURDAY, seatsRemaining = 6, capacity = 18, soldOut = false),
                        ),
                    ),
                    facets = facets(filter = q.filter, arabic = arabic),
                )
            }
            home = { AppResult.Success(home(arabic)) }
        }
    }

    /**
     * A chip nothing matches: the chips stay (so the filter can be changed back), with the filter's
     * own "no trips" line under "All trips", never the whole-screen "no trips".
     */
    @Test
    fun homeFilteredEmptyArabic() = homeFilteredEmpty(AppLanguage.ARABIC)

    @Test
    fun homeFilteredEmptyEnglish() = homeFilteredEmpty(AppLanguage.ENGLISH)

    private fun homeFilteredEmpty(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snapEach("home_filtered_empty", language, act = { selectFilter("under_400") }) {
            listFiltered = { q ->
                val chips = facets(filter = q.filter, arabic = arabic)
                if (q.filter == null) page(listOf(trip(1)), facets = chips) else page(emptyList(), facets = chips)
            }
            home = { AppResult.Success(HomeDto()) }
        }
    }

    /**
     * No trips on sale, but Home has sections (M4-M1a review #9): the sections, then "All trips" and one
     * "no trips" sentence, without the count line or the all-zero chips (M4-M1 review #1).
     */
    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun homeListEmptyWithSectionsArabic() = homeListEmptyWithSections(AppLanguage.ARABIC)

    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun homeListEmptyWithSectionsEnglish() = homeListEmptyWithSections(AppLanguage.ENGLISH)

    private fun homeListEmptyWithSections(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snapEach("home_list_empty_with_sections", language) {
            // As the server answers an empty catalogue: every chip counts 0.
            listTrips = { page(emptyList(), facets = facets(arabic = arabic).map { it.copy(count = 0) }) }
            home = { AppResult.Success(home(arabic)) }
        }
    }

    // The list is in, Home is not: still the one loading state, so the list does not jump later.
    @Test
    fun homeLoading() =
        snapEach("home_loading") {
            listTrips = { loadedPage(dawn = "Dawn on Lake Burullus" to "Most booked", kayak = "Kayak" to "Flamingo season") }
            home = { awaitCancellation() }
        }

    // Home answered but the list failed: the list's error is the screen, as before.
    @Test
    fun homeError() =
        snapEach("home_error", settle = true) {
            listTrips = { AppResult.Failure(AppError.Network) }
            home = { AppResult.Success(home(arabic = false)) }
        }

    /**
     * The seeded Home's trailing `note` (M4-M7), scrolled to: the footer under the last card, as in the
     * handoff (user decision 2026-10-11), drawn once the list's last page is in.
     */
    @Test
    fun homeNoteArabic() = homeNote(AppLanguage.ARABIC)

    @Test
    fun homeNoteEnglish() = homeNote(AppLanguage.ENGLISH)

    private fun homeNote(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snapEach("home_note", language, scrollToKey = homeSectionKey(NOTE_ID)) {
            listTrips =
                {
                    loadedPage(
                        dawn =
                            (if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus") to
                                (if (arabic) "الأكثر حجزًا" else "Most booked"),
                        kayak =
                            (if (arabic) "قنوات قطّاعي البوص بالكياك" else "Reed-cutters' channels, by kayak") to
                                (if (arabic) "موسم الفلامنجو" else "Flamingo season"),
                        facets = facets(arabic = arabic),
                    )
                }
            home = { AppResult.Success(home(arabic)) }
        }
    }

    /**
     * A note between sections (M4-M7) stays where the server put it, here between the featured row and
     * the categories; only the notes that end Home go under the list.
     */
    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun homeNoteMidListArabic() = homeNoteMidList(AppLanguage.ARABIC)

    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun homeNoteMidListEnglish() = homeNoteMidList(AppLanguage.ENGLISH)

    private fun homeNoteMidList(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snapEach("home_note_mid_list", language) {
            listTrips = {
                loadedPage(
                    dawn = if (arabic) "الفجر على بحيرة البرلس" to "الأكثر حجزًا" else "Dawn on Lake Burullus" to "Most booked",
                    kayak =
                        if (arabic) {
                            "قنوات قطّاعي البوص بالكياك" to "موسم الفلامنجو"
                        } else {
                            "Reed-cutters' channels, by kayak" to
                                "Flamingo season"
                        },
                    facets = facets(arabic = arabic),
                )
            }
            home = {
                val seeded = home(arabic).sections
                // The trailing note moved up between the row and the categories.
                AppResult.Success(HomeDto(listOf(seeded[0], seeded[1], seeded[3], seeded[2])))
            }
        }
    }

    /**
     * Hero slides and notes on their own (M4-M7): a slide with every hero field (badge, "from" price,
     * meta, the coral forward circle), one with none (an older server's: the plain picture and title),
     * one whose `url` action is not a button yet, so no forward circle although asked for; then a titled
     * note whose icon this build lacks (no icon, text full width).
     */
    @Test
    @Config(qualifiers = HOME_QUALIFIERS)
    fun heroSlidesAndNote() {
        var language by mutableStateOf(AppLanguage.ARABIC)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    val arabic = language == AppLanguage.ARABIC
                    val image = ImageDto(url = "https://cdn.invalid/banner.png", width = 900, height = 560)

                    fun slides(vararg banner: HomeBannerDto) =
                        BannersSectionDto(
                            id = "hero-${banner.first().id}",
                            type = "banners",
                            layout = "carousel",
                            aspectRatio = "45:28",
                            items = banner.toList(),
                        )
                    val sections =
                        listOf(
                            slides(heroSlide(arabic, image)),
                            slides(
                                HomeBannerDto(
                                    "plain",
                                    if (arabic) "موسم الفلامنجو" else "Flamingo season",
                                    image,
                                    HomeActionDto("category", "birds"),
                                ),
                            ),
                            slides(
                                HomeBannerDto(
                                    "url",
                                    if (arabic) "الأتوبيس والمرشد والغدا، كله شامل" else "Bus, guide and lunch, all in",
                                    image,
                                    HomeActionDto("url", "https://bahr.eg/about"),
                                    badge = BadgeDto(if (arabic) "من القاهرة" else "From Cairo", "success"),
                                    price = MoneyDto(220, "EGP"),
                                    meta = if (arabic) "موقف عبد المنعم رياض · كل الرحلات" else "Abdel Moneim Riad · every trip",
                                    showForward = true,
                                ),
                            ),
                            NoteSectionDto(
                                id = "titled-note",
                                type = "note",
                                title = if (arabic) "قبل ما تحجز" else "Before you book",
                                items =
                                    listOf(
                                        HomeNoteDto(
                                            if (arabic) "الأسعار بالجنيه المصري." else "Prices are in Egyptian pounds.",
                                            "a_symbol_this_build_lacks",
                                        ),
                                    ),
                            ),
                        )
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.background,
                                ).padding(vertical = BahrSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
                    ) {
                        sections.forEach { section ->
                            HomeSection(
                                section,
                                perPersonLabel = perPerson(language),
                                waitlistTags = WaitlistTags.None,
                                onTripClick = {},
                                onAction = {},
                            )
                        }
                    }
                }
            }
        }
        AppLanguage.entries.forEach { shotLanguage ->
            compose.runOnUiThread { language = shotLanguage }
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.captureScreenshot("home_hero_slides_note_${shotLanguage.tag}")
        }
    }

    /** The seed's first slide: every hero field (M4-B4). */
    private fun heroSlide(
        arabic: Boolean,
        image: ImageDto,
    ) = HomeBannerDto(
        "b1",
        if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
        image,
        HomeActionDto("trip", "trip-1"),
        badge = BadgeDto(if (arabic) "الأكثر حجزًا" else "Most booked", "secondary"),
        price = MoneyDto(450, "EGP"),
        meta = if (arabic) "بحيرة البرلس · يوم واحد" else "Lake Burullus · one day",
        showForward = true,
    )

    /** One multi-day card (M4-B0b): words for the duration, the nights badge, sold out. */
    @Test
    fun multiDayCard() {
        var language by mutableStateOf(AppLanguage.ARABIC)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(BahrSpacing.gutter)) {
                        TripCard(trip = desert(language == AppLanguage.ARABIC), perPersonLabel = perPerson(language), onClick = {})
                    }
                }
            }
        }
        AppLanguage.entries.forEach { shotLanguage ->
            compose.runOnUiThread { language = shotLanguage }
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.captureScreenshot("trip_card_multi_day_${shotLanguage.tag}")
        }
    }

    /**
     * M4-M5: the device is on the waiting list of the sold-out date these cards show, so each carries
     * the "Waiting list" tag beside its "Sold out" pill: a list card, and the compact card of a Home row.
     */
    @Test
    fun waitlistTagCard() {
        var language by mutableStateOf(AppLanguage.ARABIC)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    val arabic = language == AppLanguage.ARABIC
                    val kayak =
                        trip(
                            2,
                            title = if (arabic) "قنوات قطّاعي البوص بالكياك" else "Reed-cutters' channels, by kayak",
                            priceEgp = 520,
                            durationLabel = "05:00 → 19:00",
                            nextDeparture = NextDepartureDto(SATURDAY, seatsRemaining = 0, capacity = 18, soldOut = true),
                        )
                    Column(
                        modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(BahrSpacing.gutter),
                        verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
                    ) {
                        TripCard(trip = kayak, perPersonLabel = perPerson(language), onClick = {}, waitlisted = true)
                        TripCard(
                            trip = kayak,
                            perPersonLabel = perPerson(language),
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(COMPACT_CARD_FRACTION),
                            compact = true,
                            waitlisted = true,
                        )
                    }
                }
            }
        }
        AppLanguage.entries.forEach { shotLanguage ->
            compose.runOnUiThread { language = shotLanguage }
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.captureScreenshot("trip_card_waitlist_tag_${shotLanguage.tag}")
        }
    }

    private fun perPerson(language: AppLanguage) = if (language == AppLanguage.ARABIC) "للفرد" else "per person"

    private fun desert(arabic: Boolean) =
        trip(
            9,
            title = if (arabic) "الصحرا البيضا، بمبيت" else "The White Desert, overnight",
            priceEgp = 2400,
            durationLabel = if (arabic) "يومان · ليلة واحدة" else "2 days · 1 night",
            nights = 1,
            badge = BadgeDto(label = if (arabic) "مبيت" else "Overnight", tone = "quaternary"),
            nextDeparture =
                NextDepartureDto(THURSDAY, returnDate = FRIDAY, seatsRemaining = 0, capacity = 14, soldOut = true),
        )

    /**
     * The seeded Home: hero carousel (3 banners, the first with every hero field, M4-B4), the featured
     * row, the category chips, and the trailing note (M4-M7).
     */
    private fun home(arabic: Boolean): HomeDto {
        fun t(
            ar: String,
            en: String,
        ) = if (arabic) ar else en
        val image = ImageDto(url = "https://cdn.invalid/banner.png", width = 900, height = 560)
        return HomeDto(
            listOf(
                BannersSectionDto(
                    id = "hero",
                    type = "banners",
                    layout = "carousel",
                    aspectRatio = "45:28",
                    items =
                        listOf(
                            heroSlide(arabic, image),
                            HomeBannerDto("b2", t("موسم الفلامنجو", "Flamingo season"), image, HomeActionDto("category", "birds")),
                            HomeBannerDto("b3", null, image, HomeActionDto("none")),
                        ),
                ),
                TripsSectionDto(
                    id = "featured",
                    type = "trips",
                    title = t("مختارات", "Featured trips"),
                    layout = "row",
                    items =
                        listOf(
                            trip(
                                1,
                                title = t("الفجر على بحيرة البرلس", "Dawn on Lake Burullus"),
                                nextDeparture = NextDepartureDto(SATURDAY, seatsRemaining = 6, capacity = 18, soldOut = false),
                            ),
                            desert(arabic),
                        ),
                ),
                CategoriesSectionDto(
                    id = "kinds",
                    type = "categories",
                    title = t("تصفح حسب النوع", "Browse by kind"),
                    layout = "row",
                    items =
                        listOf(
                            HomeCategoryDto("on_the_boat", t("على المركب", "On the boat"), "sailing", "primary"),
                            HomeCategoryDto("birds", t("طيور", "Birds"), "flutter_dash", "secondary"),
                            HomeCategoryDto("night_trips", t("رحلات بالليل", "Night trips"), "bedtime", "quaternary"),
                        ),
                ),
                NoteSectionDto(
                    id = NOTE_ID,
                    type = "note",
                    layout = "row",
                    items =
                        listOf(
                            HomeNoteDto(
                                t(
                                    "كل الرحلات بتتحرك من موقف عبد المنعم رياض في القاهرة، وميعاد الأتوبيس مكتوب في صفحة كل رحلة.",
                                    "Every trip leaves from Abdel Moneim Riad in Cairo; each trip's page has its bus time.",
                                ),
                                "directions_bus",
                            ),
                        ),
                ),
            ),
        )
    }

    private fun snapEach(
        prefix: String,
        vararg languages: AppLanguage = AppLanguage.entries.toTypedArray(),
        header: @Composable (gap: Modifier) -> Unit = {},
        settle: Boolean = false,
        act: TripListViewModel.() -> Unit = {},
        scrollToKey: Any? = null,
        stub: FakeTripRepository.() -> Unit,
    ) {
        val viewModel = TripListViewModel(FakeTripRepository().apply(stub), waitlistMemory())
        var language by mutableStateOf(languages.first())
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        // onMap: the app always passes it, so Home's goldens show the "Map" pill (M4-M2).
                        TripListScreen(onTripClick = {}, header = header, onMap = {}, viewModel = viewModel)
                    }
                }
            }
        }
        compose.waitForIdle()
        // A tap on Home before the shots (a filter chip): the list reloads and settles under it.
        compose.runOnUiThread { viewModel.act() }
        compose.waitForIdle()
        languages.forEach { shotLanguage ->
            compose.runOnUiThread { language = shotLanguage }
            compose.waitForIdle()
            // A language switch re-keys the screen, so the list's Paging presenter starts over: cached
            // cards come back at once, a cached error a pass later. The error shots let it land; the
            // loading shots must not, or their spinner frame would move.
            if (settle) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            compose.mainClock.advanceTimeByFrame()
            scrollToKey?.let {
                // The page's own list: Home's rows scroll by key too, sideways.
                compose
                    .onNode(hasScrollToKeyAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToKey(it)
                compose.waitForIdle()
                compose.mainClock.advanceTimeByFrame()
            }
            compose.captureScreenshot("${prefix}_${shotLanguage.tag}")
        }
    }

    private companion object {
        val SATURDAY = LocalDate(2026, 10, 10)
        val THURSDAY = LocalDate(2026, 10, 15)
        val FRIDAY = LocalDate(2026, 10, 16)
        const val HOME_QUALIFIERS = "en-w360dp-h1400dp-xhdpi"
        const val NOTE_ID = "note"

        /** About a Home row card's share of the screen (the row's own fraction is internal to HomeSections). */
        const val COMPACT_CARD_FRACTION = 0.75f
    }
}
