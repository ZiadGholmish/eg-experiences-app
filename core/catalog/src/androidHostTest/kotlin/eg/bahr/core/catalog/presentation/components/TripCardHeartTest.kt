package eg.bahr.core.catalog.presentation.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.catalog.data.LikeState
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.LIKE_BUTTON_TAG
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.MoneyDto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * MP-M1: the card's heart for screen readers (a toggle with its state, "Like" and the spoken count) and for taps (the
 * heart's own, never the card's open).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class TripCardHeartTest {
    @get:Rule
    val compose = createComposeRule()

    private var likes = 0
    private var opens = 0

    private fun show(
        like: LikeState,
        language: AppLanguage = AppLanguage.ENGLISH,
    ) = compose.setContent {
        ProvideAppLanguage(language) {
            BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                TripCard(CARD, "per person", onClick = { opens++ }, like = like, onLike = { likes++ })
            }
        }
    }

    @Test
    fun aLikedHeartIsAnOnToggleWithItsCount() {
        show(LikeState(liked = true, count = 12))
        compose
            .onNodeWithTag(LIKE_BUTTON_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Like, 12 likes")))
    }

    @Test
    fun anUnlikedHeartIsOff() {
        show(LikeState(liked = false, count = 1))
        compose
            .onNodeWithTag(LIKE_BUTTON_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Like, 1 like")))
    }

    @Test
    fun arabicSaysItInArabic() {
        show(LikeState(liked = false, count = 2), AppLanguage.ARABIC)
        compose
            .onNodeWithTag(LIKE_BUTTON_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("عجبني, إعجابين")))
    }

    @Test
    fun aTapOnTheHeartLikesAndDoesNotOpenTheTrip() {
        show(LikeState(liked = false, count = 0))
        compose.onNodeWithTag(LIKE_BUTTON_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, likes)
        assertEquals(0, opens)
    }

    private companion object {
        val CARD =
            TripCardDto(
                slug = "burullus-dawn",
                title = "Dawn on Lake Burullus",
                durationLabel = "05:00 → 22:00",
                price = MoneyDto(amount = 450, currencyCode = "EGP"),
            )
    }
}
