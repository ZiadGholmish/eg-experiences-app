package eg.bahr.core.catalog.presentation.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the trip page and the host page offer "See all reviews" (M4-M12): only under a full preview
 * (the server's 3) that the summary counts more than.
 */
class SeeAllReviewsRuleTest {
    @Test
    fun anAuthoredCountWithNoReviewsGetsNoLink() {
        assertFalse(showsSeeAllReviews(count = 64, previewed = 0))
    }

    @Test
    fun aShortPreviewIsEveryReviewThereIs() {
        assertFalse(showsSeeAllReviews(count = 37, previewed = REVIEW_PREVIEW_SIZE - 1))
    }

    @Test
    fun aFullPreviewThatShowsEveryReviewGetsNoLink() {
        assertFalse(showsSeeAllReviews(count = REVIEW_PREVIEW_SIZE, previewed = REVIEW_PREVIEW_SIZE))
    }

    @Test
    fun aFullPreviewWithMoreBehindItGetsTheLink() {
        assertTrue(showsSeeAllReviews(count = REVIEW_PREVIEW_SIZE + 1, previewed = REVIEW_PREVIEW_SIZE))
    }
}
