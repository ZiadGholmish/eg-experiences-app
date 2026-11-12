package eg.bahr.core.catalog.presentation.media

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.ReviewDto
import eg.bahr.core.catalog.presentation.components.REVIEW_PHOTO_TAG
import eg.bahr.core.catalog.presentation.components.ReviewCard
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The viewer over a screen (MV-M3): a review's photo opens it on that photo, TalkBack can open it too ("View full
 * screen"), the page under it is out of reach of a screen reader meanwhile, and Back closes the viewer rather than
 * leaving the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class MediaViewerHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val review =
        ReviewDto(
            author = "Mahmoud A.",
            rating = 5,
            photos =
                List(
                    3,
                ) { ImageDto(url = "http://127.0.0.1:9/review-$it.png", width = 900, height = 560, alt = "Review photo ${it + 1}") },
        )

    private fun showReview() {
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    MediaViewerHost { Column { ReviewCard(review) } }
                }
            }
        }
    }

    @Test
    fun `a review photo opens the viewer on that photo`() {
        showReview()

        compose.onAllNodesWithTag(REVIEW_PHOTO_TAG)[1].performClick()
        compose.waitForIdle()

        compose
            .onNodeWithTag(
                MEDIA_VIEWER_COUNTER_TAG,
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("2 of 3")))
    }

    @Test
    fun `a review photo keeps its name and offers "View full screen" to a screen reader`() {
        showReview()

        val photo = compose.onAllNodesWithTag(REVIEW_PHOTO_TAG)[0]
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Review photo 1")))
        val label = photo.fetchSemanticsNode().config[SemanticsActions.OnClick].label
        assertEquals("View full screen", label)
    }

    @Test
    fun `while open, the page under it is hidden from a screen reader`() {
        showReview()

        compose.onAllNodesWithTag(REVIEW_PHOTO_TAG)[0].performClick()
        compose.waitForIdle()

        assertEquals(1, compose.onAllNodesWithTag(MEDIA_VIEWER_TAG).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag(REVIEW_PHOTO_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `Back closes the viewer, and the screen stays`() {
        showReview()
        compose.onAllNodesWithTag(REVIEW_PHOTO_TAG)[0].performClick()
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithTag(MEDIA_VIEWER_TAG).fetchSemanticsNodes().size)
        assertTrue(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks(), "the viewer takes Back")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodesWithTag(MEDIA_VIEWER_TAG).fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithTag(REVIEW_PHOTO_TAG).fetchSemanticsNodes().size)
        assertTrue(!compose.activity.isFinishing, "Back did not leave the screen")
    }

    @Test
    fun `the close button closes it`() {
        showReview()
        compose.onAllNodesWithTag(REVIEW_PHOTO_TAG)[2].performClick()
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithTag(MEDIA_VIEWER_TAG).fetchSemanticsNodes().size)

        compose.onNodeWithTag(MEDIA_VIEWER_CLOSE_TAG).performClick()
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodesWithTag(MEDIA_VIEWER_TAG).fetchSemanticsNodes().size)
    }
}
