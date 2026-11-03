package eg.bahr.feature.reviews.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.reviews.data.FakeWriteReviewRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The review page (M3-M4) × {ar, en} × {empty, stars missing, filled with photos, submitting, sent, already reviewed,
 * error}, from the stateless content. The photo tiles show their ground: the bytes here are not an image, so no
 * shot depends on a decoder's timing. Reduced motion holds the sent check still on its last frame.
 *
 * Record: `./gradlew :feature:reviews:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h900dp-xhdpi")
class WriteReviewScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyArabic() = shoot("write_review_empty", AppLanguage.ARABIC, empty(AppLanguage.ARABIC))

    @Test
    fun emptyEnglish() = shoot("write_review_empty", AppLanguage.ENGLISH, empty(AppLanguage.ENGLISH))

    /** "Send" with no stars: the stars' error under them. */
    @Test
    fun starsMissingArabic() = shoot("write_review_stars_missing", AppLanguage.ARABIC, empty(AppLanguage.ARABIC).copy(validated = true))

    @Test
    fun starsMissingEnglish() = shoot("write_review_stars_missing", AppLanguage.ENGLISH, empty(AppLanguage.ENGLISH).copy(validated = true))

    /** Four stars, a text, three photos (each with its remove button) and the "Add photos" tile. */
    @Test
    fun filledArabic() = shoot("write_review_filled", AppLanguage.ARABIC, filled(AppLanguage.ARABIC))

    @Test
    fun filledEnglish() = shoot("write_review_filled", AppLanguage.ENGLISH, filled(AppLanguage.ENGLISH))

    /** Five photos: no "Add photos" tile; a text past the limit, its count and box red. */
    @Test
    fun fullArabic() = shoot("write_review_full", AppLanguage.ARABIC, full(AppLanguage.ARABIC))

    @Test
    fun fullEnglish() = shoot("write_review_full", AppLanguage.ENGLISH, full(AppLanguage.ENGLISH))

    /** Sending: the form dimmed, the button busy. */
    @Test
    fun submittingArabic() = shoot("write_review_submitting", AppLanguage.ARABIC, filled(AppLanguage.ARABIC).copy(submitting = true))

    @Test
    fun submittingEnglish() = shoot("write_review_submitting", AppLanguage.ENGLISH, filled(AppLanguage.ENGLISH).copy(submitting = true))

    @Test
    fun submittedArabic() = shoot("write_review_submitted", AppLanguage.ARABIC, sent(AppLanguage.ARABIC, ReviewOutcome.Submitted))

    @Test
    fun submittedEnglish() = shoot("write_review_submitted", AppLanguage.ENGLISH, sent(AppLanguage.ENGLISH, ReviewOutcome.Submitted))

    @Test
    fun alreadyReviewedArabic() = shoot("write_review_already", AppLanguage.ARABIC, sent(AppLanguage.ARABIC, ReviewOutcome.AlreadyReviewed))

    @Test
    fun alreadyReviewedEnglish() =
        shoot("write_review_already", AppLanguage.ENGLISH, sent(AppLanguage.ENGLISH, ReviewOutcome.AlreadyReviewed))

    /** A photo the server refused (UNSUPPORTED_MEDIA_TYPE), and one the picker could not read. */
    @Test
    fun errorArabic() = shoot("write_review_error", AppLanguage.ARABIC, failed(AppLanguage.ARABIC))

    @Test
    fun errorEnglish() = shoot("write_review_error", AppLanguage.ENGLISH, failed(AppLanguage.ENGLISH))

    /** A network failure: the app's own message for it. */
    @Test
    fun networkErrorArabic() =
        shoot(
            "write_review_network_error",
            AppLanguage.ARABIC,
            filled(AppLanguage.ARABIC).copy(problem = SubmitProblem.Other(AppError.Network)),
        )

    @Test
    fun networkErrorEnglish() =
        shoot(
            "write_review_network_error",
            AppLanguage.ENGLISH,
            filled(AppLanguage.ENGLISH).copy(problem = SubmitProblem.Other(AppError.Network)),
        )

    @Test
    fun `through the view model - a star - a text and Send reach the repository - then Done`() {
        val repository = FakeWriteReviewRepository()
        val vm = WriteReviewViewModel("BHR-7Q2K", "Burullus at dawn", repository)
        var done = 0
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                WriteReviewScreen(ref = "BHR-7Q2K", title = "Burullus at dawn", onBack = {}, onDone = { done++ }, viewModel = vm)
            }
        }
        compose.onNodeWithTag(starTag(4)).performClick()
        compose.onNodeWithTag(starTag(4)).assertIsSelected()
        compose.onNodeWithTag(REVIEW_TEXT_TAG).performTextInput("Calm lake")
        compose.onNodeWithTag(REVIEW_SUBMIT_TAG).performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag(REVIEW_DONE_TAG).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(REVIEW_DONE_TAG).performClick()

        assertEquals(4, repository.requests.single().rating)
        assertEquals("Calm lake", repository.requests.single().text)
        assertEquals(1, done)
    }

    @Test
    fun `a photo's remove button removes that photo`() {
        val removed = mutableListOf<Int>()
        val state = filled(AppLanguage.ENGLISH)
        compose.setContent {
            Themed(AppLanguage.ENGLISH) { WriteReviewContent(state, WriteReviewActions(onRemovePhoto = { removed += it })) }
        }
        compose.onNodeWithTag(removePhotoTag(2)).performClick()
        assertEquals(listOf(state.photos[1].id), removed)
    }

    private fun title(language: AppLanguage) = if (language == AppLanguage.ARABIC) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus"

    private fun text(language: AppLanguage) =
        if (language == AppLanguage.ARABIC) {
            "المركب والطيور كانوا تحفة، والريّس أشرف وقف الموتور عشان نسمع العصافير. الأتوبيس اتأخر نص ساعة الصبح."
        } else {
            "The boat and the birds were wonderful, and Ashraf cut the motor so we could hear them. The bus was half an hour late."
        }

    private fun photos(count: Int) = List(count) { ReviewPhoto(id = it, jpeg = byteArrayOf(it.toByte())) }

    private fun empty(language: AppLanguage) = WriteReviewUiState(title = title(language))

    private fun filled(language: AppLanguage) = empty(language).copy(rating = 4, text = text(language), photos = photos(3))

    private fun full(language: AppLanguage) = filled(language).copy(photos = photos(5), text = text(language).repeat(TOO_LONG_REPEATS))

    private fun failed(language: AppLanguage) =
        filled(language).copy(validated = true, photoFailed = true, problem = SubmitProblem.PhotoType)

    private fun sent(
        language: AppLanguage,
        outcome: ReviewOutcome,
    ) = filled(language).copy(outcome = outcome)

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        state: WriteReviewUiState,
    ) {
        compose.setContent { Themed(language) { WriteReviewContent(state, WriteReviewActions()) } }
        if (state.outcome != null) {
            // The check's file is read off the main thread: idle alone would shoot its empty box.
            compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    @Composable
    private fun Themed(
        language: AppLanguage,
        content: @Composable () -> Unit,
    ) {
        ProvideAppLanguage(language) {
            BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English, reducedMotion = true) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L

        /** Enough copies of the sample text to pass the 2000-character limit. */
        const val TOO_LONG_REPEATS = 20
    }
}
