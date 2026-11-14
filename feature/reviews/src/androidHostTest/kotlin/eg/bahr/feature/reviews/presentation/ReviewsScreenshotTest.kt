package eg.bahr.feature.reviews.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.RatingSummaryDto
import eg.bahr.core.catalog.model.ReviewDto
import eg.bahr.core.catalog.model.StarCountDto
import eg.bahr.core.catalog.presentation.components.REVIEW_AUTHOR_LINK_TAG
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.reviews.data.FakeReviewsRepository
import eg.bahr.feature.reviews.data.TripFixtures.page
import eg.bahr.feature.reviews.data.TripFixtures.trip
import eg.bahr.feature.reviews.model.ReviewPageDto
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
import kotlin.test.assertEquals

/**
 * "See all reviews" (M4-M11) × {ar, en} × {loading, loaded, filtered, filter empty, error, not available},
 * through the real view model on a fake repository. The server answers in the request's language, so
 * each language gets its own copy (the seeded burullus-dawn reviews, a rating-only one among them).
 *
 * Record: `./gradlew :feature:reviews:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h1200dp-xhdpi")
class ReviewsScreenshotTest {
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

    /** The heading at once (the opener's title), the list loading; no chips until the summary is in. */
    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun loadingArabic() = loading(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun loadingEnglish() = loading(AppLanguage.ENGLISH)

    /** The skeleton in dark mode (M3-M14). */
    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun loadingDarkArabic() = loading(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun loadingDarkEnglish() = loading(AppLanguage.ENGLISH, BahrThemeName.Dark)

    private fun loading(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) = snap(
        if (theme == BahrThemeName.Dark) "reviews_loading_dark" else "reviews_loading",
        language,
        tripVm(FakeReviewsRepository(reviews = { awaitCancellation() }), language),
        theme = theme,
    )

    /** A trip's reviews, newest first: the summary, the chips with their counts, stars on every card, a rating-only one. */
    @Test
    fun loadedArabic() = loaded(AppLanguage.ARABIC)

    @Test
    fun loadedEnglish() = loaded(AppLanguage.ENGLISH)

    private fun loaded(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snap("reviews_loaded", language, tripVm(FakeReviewsRepository(reviews = { answer(reviews(arabic), DAWN_SUMMARY) }), language))
    }

    /**
     * MP-M3: every reviewer's name opens their profile page, the public one with its id and a private one (no
     * `authorPublicId`) with null; both are buttons labelled "Open <name>’s profile".
     */
    @Test
    fun `every reviewer name opens the profile page - public with its id - private with null`() {
        val items = reviews(arabic = false).mapIndexed { i, r -> if (i == 0) r.copy(authorPublicId = "p-1") else r }
        val opened = mutableListOf<String?>()
        show(
            AppLanguage.ENGLISH,
            tripVm(FakeReviewsRepository(reviews = { answer(items, DAWN_SUMMARY) }), AppLanguage.ENGLISH),
            onTravellerClick = { opened += it },
        )
        val links = compose.onAllNodesWithTag(REVIEW_AUTHOR_LINK_TAG)
        links.assertCountEquals(items.size)
        links[0].performClick()
        links[1].performClick()
        assertEquals(listOf("p-1", null), opened)
        listOf(0 to "Tarek N.", 1 to "Heba R.").forEach { (index, author) ->
            val node = links[index].fetchSemanticsNode()
            assertEquals("Open $author’s profile", node.config.getOrNull(SemanticsActions.OnClick)?.label)
            assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        }
    }

    /**
     * M3-M4: a verified review with the traveller's photos (sized squares on their ground: no image loads in a
     * screenshot) above the others.
     */
    @Test
    fun photosArabic() = photos(AppLanguage.ARABIC)

    @Test
    fun photosEnglish() = photos(AppLanguage.ENGLISH)

    private fun photos(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        val withPhotos =
            ReviewDto(
                author = if (arabic) "منى س." else "Mona S.",
                dateISO = LocalDate(2026, 10, 20),
                body = if (arabic) "أحلى شروق شفته في حياتي." else "The best sunrise I have ever seen.",
                tone = "secondary",
                rating = 5,
                verified = true,
                photos = List(PHOTO_COUNT) { ImageDto(url = "https://photos.invalid/review-$it.jpg", width = 1600, height = 1200) },
            )
        snap(
            "reviews_photos",
            language,
            tripVm(FakeReviewsRepository(reviews = { answer(listOf(withPhotos) + reviews(arabic), DAWN_SUMMARY) }), language),
        )
    }

    /**
     * M4-M15: a host's reviews, all their trips: the host's name under the heading, in the page's
     * language (M4-B10), with the Trip filter on "All trips".
     */
    @Test
    fun hostArabic() = host(AppLanguage.ARABIC)

    @Test
    fun hostEnglish() = host(AppLanguage.ENGLISH)

    private fun host(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        val repository =
            FakeReviewsRepository(reviews = { answer(reviews(arabic), HOST_SUMMARY) }, listFiltered = { page(hostTrips(arabic)) })
        snap("reviews_host", language, hostVm(repository, language))
        compose.onNodeWithTag(REVIEWS_TRIP_FILTER_TAG).assertIsDisplayed()
    }

    /**
     * A host's reviews narrowed to one trip and to 5 stars: the Trip filter's chip, the trip's title under
     * the heading, the trip's own summary, the 5★ chip selected.
     */
    @Test
    fun filteredArabic() = filtered(AppLanguage.ARABIC)

    @Test
    fun filteredEnglish() = filtered(AppLanguage.ENGLISH)

    private fun filtered(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        val repository =
            FakeReviewsRepository(
                reviews = { r ->
                    answer(
                        reviews(arabic).filter { it.rating == r.stars },
                        if (r.trip !=
                            null
                        ) {
                            DAWN_SUMMARY
                        } else {
                            HOST_SUMMARY
                        },
                    )
                },
                listFiltered = { page(hostTrips(arabic)) },
            )
        snap("reviews_filtered", language, hostVm(repository, language)) { vm ->
            vm.selectTrip(DAWN)
            vm.selectStars(5)
        }
        compose.onNodeWithTag(REVIEWS_TRIP_FILTER_TAG).assertIsDisplayed()
    }

    /** One of the host's trips with no reviews yet: its empty summary, and "nothing matches" for the filter. */
    @Test
    fun filterEmptyArabic() = filterEmpty(AppLanguage.ARABIC)

    @Test
    fun filterEmptyEnglish() = filterEmpty(AppLanguage.ENGLISH)

    private fun filterEmpty(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        val repository =
            FakeReviewsRepository(
                reviews = { r -> if (r.trip == SUNSET) answer(emptyList(), EMPTY_SUMMARY) else answer(reviews(arabic), HOST_SUMMARY) },
                listFiltered = { page(hostTrips(arabic)) },
            )
        snap("reviews_filter_empty", language, hostVm(repository, language)) { vm -> vm.selectTrip(SUNSET) }
        compose.onNodeWithTag(REVIEWS_EMPTY_TAG).assertIsDisplayed()
    }

    /** No connection on the first page: the heading stays, the error with its retry under it. */
    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun errorArabic() = error(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun errorEnglish() = error(AppLanguage.ENGLISH)

    private fun error(language: AppLanguage) =
        snap("reviews_error", language, tripVm(FakeReviewsRepository(reviews = { AppResult.Failure(AppError.Network) }), language))

    /** NOT_FOUND (the trip unpublished since): "not available", no retry, the back button still there. */
    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun notAvailableArabic() = notAvailable(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun notAvailableEnglish() = notAvailable(AppLanguage.ENGLISH)

    private fun notAvailable(language: AppLanguage) {
        snap("reviews_not_available", language, tripVm(FakeReviewsRepository(reviews = { NOT_FOUND }), language))
        compose.onNodeWithTag(REVIEWS_NOT_AVAILABLE_TAG).assertIsDisplayed()
    }

    // ---------- Fixtures and plumbing ----------

    private fun tripVm(
        repository: FakeReviewsRepository,
        language: AppLanguage,
    ) = ReviewsViewModel(trip = DAWN, host = null, title = dawnTitle(language == AppLanguage.ARABIC), repository = repository)

    /** [title] is the host's name as the host page showed it: in the page's language since M4-B10. */
    private fun hostVm(
        repository: FakeReviewsRepository,
        language: AppLanguage,
    ) = ReviewsViewModel(
        trip = null,
        host = HOST,
        title = if (language == AppLanguage.ARABIC) "أشرف البحر" else "Ashraf El Bahr",
        repository = repository,
    )

    private fun dawnTitle(arabic: Boolean) = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus"

    private fun hostTrips(arabic: Boolean) =
        listOf(
            trip(1, title = dawnTitle(arabic)).copy(slug = DAWN),
            trip(2, title = if (arabic) "الغروب في البرلس" else "Sunset at Burullus").copy(slug = SUNSET),
        )

    private fun answer(
        items: List<ReviewDto>,
        summary: RatingSummaryDto,
    ) = AppResult.Success(
        ReviewPageDto(items = items, page = 0, size = items.size, totalItems = items.size.toLong(), totalPages = 1, rating = summary),
    )

    /** The seeded burullus-dawn reviews (M4-B8), newest first; Heba's is a rating only. */
    private fun reviews(arabic: Boolean) =
        listOf(
            ReviewDto(
                author = if (arabic) "طارق ن." else "Tarek N.",
                dateISO = LocalDate(2026, 10, 3),
                body =
                    if (arabic) {
                        "المركب والطيور كانوا تحفة، بس الأتوبيس اتأخر نص ساعة الصبح."
                    } else {
                        "The boat and the birds were wonderful, but the bus was half an hour late in the morning."
                    },
                partyLabel = if (arabic) "مع مراتي" else "With my wife",
                tone = "primary",
                rating = 4,
            ),
            ReviewDto(author = if (arabic) "هبة ر." else "Heba R.", dateISO = LocalDate(2026, 9, 28), tone = "success", rating = 5),
            ReviewDto(
                author = if (arabic) "ندى ح." else "Nada H.",
                dateISO = LocalDate(2026, 9, 21),
                body =
                    if (arabic) {
                        "الساعة 09:00 البحيرة ساكنة تمامًا وأشرف بيقفل الموتور علشان تسمع الطيور."
                    } else {
                        "At 09:00 the lake is completely flat and silent, and Ashraf cuts the motor so you hear the birds."
                    },
                partyLabel = if (arabic) "مع 3 أصحاب" else "Went with 3 friends",
                tone = "quaternary",
                rating = 5,
            ),
            ReviewDto(
                author = if (arabic) "محمود ع." else "Mahmoud A.",
                dateISO = LocalDate(2026, 9, 14),
                body = if (arabic) "يوم طويل، بس الغدا لوحده يستاهل." else "Long day, but worth it for the lunch alone.",
                tone = "tertiary",
                rating = 5,
            ),
        )

    private fun snap(
        prefix: String,
        language: AppLanguage,
        vm: ReviewsViewModel,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
        act: (ReviewsViewModel) -> Unit = {},
    ) {
        show(language, vm, theme)
        // The filters are tapped once the first answer (and the host's trips) are in, as a user would.
        compose.runOnUiThread { act(vm) }
        settle()
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private fun show(
        language: AppLanguage,
        vm: ReviewsViewModel,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
        onTravellerClick: ((publicId: String?) -> Unit)? = null,
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
                            ReviewsScreen(
                                trip = null,
                                host = null,
                                title = null,
                                onBack = {},
                                onTravellerClick = onTravellerClick,
                                viewModel = vm,
                            )
                        }
                    }
                }
            }
        }
        settle()
    }

    /** The paging presenter writes its state in an effect: frames, the longest animation, frames again. */
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
        const val DAWN = "burullus-dawn"
        const val SUNSET = "burullus-sunset"
        const val HOST = "ashraf-el-bahr"
        const val SETTLE_FRAMES = 2

        fun summary(
            average: Double?,
            vararg counts: Int,
        ) = RatingSummaryDto(
            average = average,
            count = counts.sum(),
            distribution = counts.mapIndexed { i, count -> StarCountDto(stars = 5 - i, count = count) },
        )

        /** Five: more than a card's width, so the row scrolls (the server's limit per review). */
        const val PHOTO_COUNT = 5
        val DAWN_SUMMARY = summary(4.8, 4, 1, 0, 0, 0)
        val HOST_SUMMARY = summary(4.7, 5, 2, 0, 0, 0)
        val EMPTY_SUMMARY = summary(null, 0, 0, 0, 0, 0)
        val NOT_FOUND = AppResult.Failure(AppError.Api("NOT_FOUND", "Not found", 404))
    }
}
