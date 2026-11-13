package eg.bahr.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eg.bahr.core.catalog.presentation.WaitlistTags
import eg.bahr.core.catalog.presentation.components.REVIEW_AUTHOR_LINK_TAG
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.home.model.HomeReviewDto
import eg.bahr.feature.home.model.HomeTripRefDto
import eg.bahr.feature.home.model.ReviewsSectionDto
import eg.bahr.feature.home.navigation.HomeAction
import eg.bahr.feature.home.presentation.components.HomeSection
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * MP-M2: Home's review card is itself a link to its trip; a reviewer whose profile is public (`authorPublicId`) has a
 * name that is a link of its own. A tap on the name opens the profile and not the trip; a tap elsewhere still opens the
 * trip. Goldens × {ar, en}: the linked name beside a plain one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class HomeReviewAuthorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the name opens the profile - the rest of the card the trip`() {
        val actions = mutableListOf<HomeAction>()
        show(AppLanguage.ENGLISH) { actions += it }

        compose.onNodeWithTag(REVIEW_AUTHOR_LINK_TAG).performClick()
        assertEquals(listOf<HomeAction>(HomeAction.OpenTraveller(PUBLIC_ID)), actions)

        compose.onNodeWithText(LINKED_BODY).performClick()
        assertEquals(HomeAction.OpenTrip("burullus-dawn"), actions.last())
        assertEquals(2, actions.size)
    }

    @Test
    fun linkedArabic() = snap(AppLanguage.ARABIC)

    @Test
    fun linkedEnglish() = snap(AppLanguage.ENGLISH)

    private fun snap(language: AppLanguage) {
        show(language) {}
        compose.waitForIdle()
        compose.captureScreenshot("home_review_author_link_${language.tag}")
    }

    private fun show(
        language: AppLanguage,
        onAction: (HomeAction) -> Unit,
    ) {
        val arabic = language == AppLanguage.ARABIC
        val section = parseHomeSections(listOf(reviews(arabic))) { error(it) }.single()
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (arabic) BahrLocale.Arabic else BahrLocale.English) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(vertical = BahrSpacing.lg)) {
                            HomeSection(
                                section,
                                perPersonLabel = if (arabic) "للفرد" else "per person",
                                waitlistTags = WaitlistTags.None,
                                onTripClick = {},
                                onAction = onAction,
                                likes = null,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun reviews(arabic: Boolean) =
        ReviewsSectionDto(
            id = "reviews",
            type = "reviews",
            title = if (arabic) "المسافرين بيقولوا" else "What travellers say",
            layout = "card_list",
            items =
                listOf(
                    HomeReviewDto(
                        "r1",
                        rating = 5,
                        author = if (arabic) "منى س." else "Mona S.",
                        authorPublicId = PUBLIC_ID,
                        tone = "secondary",
                        dateISO = LocalDate(2026, 10, 20),
                        verified = true,
                        body = if (arabic) "الفلامنجو وقت الشروق يستاهل الصحيان بدري." else LINKED_BODY,
                        trip = HomeTripRefDto("burullus-dawn", if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus"),
                    ),
                    HomeReviewDto(
                        "r2",
                        rating = 4,
                        author = if (arabic) "مسافر" else "A traveller",
                        tone = "quaternary",
                        dateISO = LocalDate(2026, 10, 12),
                        trip = HomeTripRefDto("white-desert", if (arabic) "الصحرا البيضا، ليلة" else "The White Desert, overnight"),
                    ),
                ),
        )

    private companion object {
        const val PUBLIC_ID = "3f9c2a7e5b1d4c8e9a0f6b2d7c4e1a95"
        const val LINKED_BODY = "The flamingos at sunrise were worth the early start."
    }
}
