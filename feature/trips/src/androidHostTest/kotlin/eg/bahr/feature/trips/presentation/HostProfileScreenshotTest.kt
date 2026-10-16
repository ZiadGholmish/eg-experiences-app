package eg.bahr.feature.trips.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import eg.bahr.core.catalog.model.NextDepartureDto
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
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.HostProfileDto
import eg.bahr.feature.trips.model.RatingSummaryDto
import eg.bahr.feature.trips.model.ReviewDto
import eg.bahr.feature.trips.model.StarCountDto
import eg.bahr.feature.trips.presentation.components.SEE_ALL_REVIEWS_TAG
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
 * The host page (M4-M10) × {ar, en} × {loading, loaded, no reviews, error, not available}, through the
 * real view model on a fake repository. The server answers in the request's language, host names
 * included since M4-B10 (أشرف البحر, ريد سي دايز), so each language gets its own copy. No image URLs: a golden must not depend on Coil or a running MinIO.
 *
 * Record: `./gradlew :feature:trips:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class HostProfileScreenshotTest {
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

    /** Text first: the card's name at once, the facts and rating shimmering, the trips loading. */
    @Test
    fun loadingArabic() = loading(AppLanguage.ARABIC)

    @Test
    fun loadingEnglish() = loading(AppLanguage.ENGLISH)

    private fun loading(language: AppLanguage) =
        snap("host_loading", language, FakeTripRepository(hostBySlug = { awaitCancellation() }, listFiltered = { awaitCancellation() }))

    /**
     * A verified person with reviews: header, rating bars, two reviews with their stars, "See all reviews"
     * (6 counted, 2 shown; M4-M11), then their trips with the count.
     */
    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun loadedArabic() = loaded(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun loadedEnglish() = loaded(AppLanguage.ENGLISH)

    private fun loaded(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snap(
            "host_loaded",
            language,
            FakeTripRepository(
                hostBySlug = { AppResult.Success(ashraf(arabic)) },
                listFiltered = { page(cards(arabic), totalItems = 2) },
            ),
            name = ashrafName(arabic),
        )
        compose.onNodeWithTag(SEE_ALL_REVIEWS_TAG).assertExists()
    }

    /**
     * M4-M12: the preview is short of the server's 3 (2 shown), so it is every real review there is,
     * though the summary counts 6: no "See all reviews".
     */
    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun allReviewsShownArabic() = allReviewsShown(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun allReviewsShownEnglish() = allReviewsShown(AppLanguage.ENGLISH)

    private fun allReviewsShown(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        val host = ashraf(arabic).let { it.copy(reviews = it.reviews.take(2)) }
        snap(
            "host_all_reviews_shown",
            language,
            FakeTripRepository(hostBySlug = { AppResult.Success(host) }, listFiltered = { page(cards(arabic), totalItems = 2) }),
            name = ashrafName(arabic),
        )
        compose.onNodeWithTag(SEE_ALL_REVIEWS_TAG).assertDoesNotExist()
    }

    /**
     * M4-M12 (user, found on device): a summary that counts reviews (6) with no review rows behind it.
     * The summary stays; no "See all reviews" under an empty preview.
     */
    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun authoredRatingOnlyArabic() = authoredRatingOnly(AppLanguage.ARABIC)

    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun authoredRatingOnlyEnglish() = authoredRatingOnly(AppLanguage.ENGLISH)

    private fun authoredRatingOnly(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        val host = ashraf(arabic).copy(reviews = emptyList())
        snap(
            "host_authored_rating_only",
            language,
            FakeTripRepository(hostBySlug = { AppResult.Success(host) }, listFiltered = { page(cards(arabic), totalItems = 2) }),
            name = ashrafName(arabic),
        )
        compose.onNodeWithTag(SEE_ALL_REVIEWS_TAG).assertDoesNotExist()
    }

    /** A company with no reviews yet (Red Sea Days in the seed): no bars, one line saying so. */
    @Test
    fun noReviewsArabic() = noReviews(AppLanguage.ARABIC)

    @Test
    fun noReviewsEnglish() = noReviews(AppLanguage.ENGLISH)

    private fun noReviews(language: AppLanguage) {
        val arabic = language == AppLanguage.ARABIC
        snap(
            "host_no_reviews",
            language,
            FakeTripRepository(
                hostBySlug = { AppResult.Success(redSeaDays(arabic)) },
                listFiltered = { page(cards(arabic).take(1), totalItems = 1) },
            ),
            name = redSeaDaysName(arabic),
        )
    }

    /**
     * M4-M15: a host with no English name gets the Arabic one on the English page (M4-B10's fallback):
     * the header and "Trips by …" carry the RTL name inside LTR text, the card's name on the first frame
     * and the profile's after it.
     */
    @Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
    @Test
    fun arabicNameEnglish() {
        val host = ashraf(arabic = false).copy(name = ashrafName(arabic = true))
        snap(
            "host_arabic_name",
            AppLanguage.ENGLISH,
            FakeTripRepository(hostBySlug = { AppResult.Success(host) }, listFiltered = { page(cards(arabic = false), totalItems = 2) }),
            name = ashrafName(arabic = true),
        )
    }

    /** No connection: the error with its retry, under the back button. */
    @Test
    fun errorArabic() = error(AppLanguage.ARABIC)

    @Test
    fun errorEnglish() = error(AppLanguage.ENGLISH)

    private fun error(language: AppLanguage) =
        snap(
            "host_error",
            language,
            FakeTripRepository(
                hostBySlug = { AppResult.Failure(AppError.Network) },
                listFiltered = { AppResult.Failure(AppError.Network) },
            ),
        )

    /** NOT_FOUND (unknown, or not approved): "not available", no retry, the back button still there. */
    @Test
    fun notAvailableArabic() = notAvailable(AppLanguage.ARABIC)

    @Test
    fun notAvailableEnglish() = notAvailable(AppLanguage.ENGLISH)

    private fun notAvailable(language: AppLanguage) =
        snap("host_not_available", language, FakeTripRepository(hostBySlug = { NOT_FOUND }, listFiltered = { NOT_FOUND }))

    // ---------- Fixtures and plumbing ----------

    private fun ashraf(arabic: Boolean) =
        HostProfileDto(
            slug = "ashraf-el-bahr",
            kind = "person",
            name = ashrafName(arabic),
            role = if (arabic) "صاحب القارب، برج البرلس" else "Boat owner, Burg El Burullus",
            bio =
                if (arabic) {
                    "صياد من الجيل التالت. بياخد الضيوف الفجر والبحيرة لسه هادية."
                } else {
                    "Third-generation fisherman. Takes guests out at dawn when the lake is still."
                },
            verified = true,
            memberSince = LocalDate(2024, 9, 14),
            tripsRun = 11,
            rating =
                RatingSummaryDto(
                    average = 4.7,
                    count = 6,
                    distribution = listOf(5 to 4, 4 to 2, 3 to 0, 2 to 0, 1 to 0).map { (stars, count) -> StarCountDto(stars, count) },
                ),
            reviews =
                listOf(
                    ReviewDto(
                        author = if (arabic) "محمود ع." else "Mahmoud A.",
                        dateISO = LocalDate(2026, 9, 21),
                        body = if (arabic) "الفلامنجو وقت الشروق كان حاجة مش طبيعية." else "The flamingos at sunrise were unreal.",
                        partyLabel = if (arabic) "مجموعة من 2" else "Party of 2",
                        tone = "primary",
                        rating = 5,
                    ),
                    ReviewDto(
                        author = if (arabic) "سلمى ك." else "Salma K.",
                        dateISO = LocalDate(2026, 9, 7),
                        body = if (arabic) "أشرف عارف كل قناة في البحيرة." else "Ashraf knows every channel of the lake.",
                        tone = "secondary",
                        rating = 4,
                    ),
                    // A full preview (the server's 3), so the page offers "See all reviews" (M4-M12).
                    ReviewDto(
                        author = if (arabic) "كريم ف." else "Karim F.",
                        dateISO = LocalDate(2026, 8, 30),
                        tone = "tertiary",
                        rating = 4,
                    ),
                ),
        )

    /** The seed's names (M4-B10): the server sends the one in the request's language. */
    private fun ashrafName(arabic: Boolean) = if (arabic) "أشرف البحر" else "Ashraf El Bahr"

    private fun redSeaDaysName(arabic: Boolean) = if (arabic) "ريد سي دايز" else "Red Sea Days"

    private fun redSeaDays(arabic: Boolean) =
        HostProfileDto(
            slug = SLUG,
            kind = "company",
            name = redSeaDaysName(arabic),
            role = if (arabic) "شركة رحلات يوم واحد، البحر الأحمر" else "Day-trip company, the Red Sea",
            bio =
                if (arabic) {
                    "فريق صغير بينظم رحلات يوم واحد من القاهرة للعين السخنة ورأس سدر."
                } else {
                    "A small team running day trips from Cairo to Ain Sokhna and Ras Sudr."
                },
            memberSince = LocalDate(2026, 10, 1),
            tripsRun = 0,
            rating = RatingSummaryDto(count = 0, distribution = (5 downTo 1).map { StarCountDto(it, 0) }),
        )

    private fun cards(arabic: Boolean) =
        listOf(
            trip(
                1,
                title = if (arabic) "يوم على البحر الأحمر في العين السخنة" else "A Red Sea beach day at Ain Sokhna",
                priceEgp = 550,
                durationLabel = "06:00 → 21:00",
                nextDeparture = NextDepartureDto(LocalDate(2026, 10, 16), seatsRemaining = 9, capacity = 20, soldOut = false),
            ),
            trip(
                2,
                title = if (arabic) "يوم كايت في رأس سدر" else "Kite day at Ras Sudr",
                priceEgp = 690,
                durationLabel = "05:30 → 21:30",
                nextDeparture = NextDepartureDto(LocalDate(2026, 10, 17), seatsRemaining = 3, capacity = 12, soldOut = false),
            ),
        )

    private fun snap(
        prefix: String,
        language: AppLanguage,
        repository: FakeTripRepository,
        name: String = redSeaDaysName(language == AppLanguage.ARABIC),
    ) {
        val vm = HostProfileViewModel(SLUG, name, null, repository, waitlistMemory())
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        HostProfileScreen(SLUG, name, null, onBack = {}, onTripClick = {}, onSeeAllReviews = {}, viewModel = vm)
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
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private companion object {
        const val SLUG = "red-sea-days"
        const val SETTLE_FRAMES = 2
        val NOT_FOUND = AppResult.Failure(AppError.Api("NOT_FOUND", "Host not found", 404))
    }
}
