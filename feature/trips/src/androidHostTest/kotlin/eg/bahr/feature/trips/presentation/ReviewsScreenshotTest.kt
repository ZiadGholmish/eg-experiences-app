package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.model.RatingSummaryDto
import eg.bahr.feature.trips.model.ReviewDto
import eg.bahr.feature.trips.model.ReviewPageDto
import eg.bahr.feature.trips.model.StarCountDto
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
 * "See all reviews" (M4-M11) × {ar, en} × {loading, loaded, filtered, filter empty, error, not available},
 * through the real view model on a fake repository. The server answers in the request's language, so
 * each language gets its own copy (the seeded burullus-dawn reviews, a rating-only one among them).
 *
 * Record: `./gradlew :feature:trips:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
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

    private fun loading(language: AppLanguage) =
        snap("reviews_loading", language, tripVm(FakeTripRepository(reviews = { awaitCancellation() }), language))

    /** A trip's reviews, newest first: the summary, the chips with their counts, stars on every card, a rating-only one. */
    @Test
    fun loadedArabic() = loaded(AppLanguage.ARABIC)

    @Test
    fun loadedEnglish() = loaded(AppLanguage.ENGLISH)

    private fun loaded(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snap("reviews_loaded", language, tripVm(FakeTripRepository(reviews = { answer(reviews(arabic), DAWN_SUMMARY) }), language))
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
        val repository = FakeTripRepository(reviews = { answer(reviews(arabic), HOST_SUMMARY) }, listFiltered = { page(hostTrips(arabic)) })
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
            FakeTripRepository(
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
            FakeTripRepository(
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
        snap("reviews_error", language, tripVm(FakeTripRepository(reviews = { AppResult.Failure(AppError.Network) }), language))

    /** NOT_FOUND (the trip unpublished since): "not available", no retry, the back button still there. */
    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun notAvailableArabic() = notAvailable(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h800dp-xhdpi")
    @Test
    fun notAvailableEnglish() = notAvailable(AppLanguage.ENGLISH)

    private fun notAvailable(language: AppLanguage) {
        snap("reviews_not_available", language, tripVm(FakeTripRepository(reviews = { NOT_FOUND }), language))
        compose.onNodeWithTag(REVIEWS_NOT_AVAILABLE_TAG).assertIsDisplayed()
    }

    // ---------- Fixtures and plumbing ----------

    private fun tripVm(
        repository: FakeTripRepository,
        language: AppLanguage,
    ) = ReviewsViewModel(trip = DAWN, host = null, title = dawnTitle(language == AppLanguage.ARABIC), repository = repository)

    /** [title] is the host's name as the host page showed it: in the page's language since M4-B10. */
    private fun hostVm(
        repository: FakeTripRepository,
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
        act: (ReviewsViewModel) -> Unit = {},
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ReviewsScreen(trip = null, host = null, title = null, onBack = {}, viewModel = vm)
                    }
                }
            }
        }
        settle()
        // The filters are tapped once the first answer (and the host's trips) are in, as a user would.
        compose.runOnUiThread { act(vm) }
        settle()
        compose.captureScreenshot("${prefix}_${language.tag}")
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

        val DAWN_SUMMARY = summary(4.8, 4, 1, 0, 0, 0)
        val HOST_SUMMARY = summary(4.7, 5, 2, 0, 0, 0)
        val EMPTY_SUMMARY = summary(null, 0, 0, 0, 0, 0)
        val NOT_FOUND = AppResult.Failure(AppError.Api("NOT_FOUND", "Not found", 404))
    }
}
