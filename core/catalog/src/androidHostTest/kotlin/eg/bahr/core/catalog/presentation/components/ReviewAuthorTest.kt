package eg.bahr.core.catalog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.catalog.model.ReviewDto
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * MP-M2: a review's author name × {ar, en} × {light, dark}: plain (no `authorPublicId`, drawn as before), a link (in
 * primary, a 44dp touch area), and a link that reads "A traveller" / "مسافر" (an account with no name and a public
 * profile, MP-B2 round 1). Then the taps: the link hands over its id, a plain name has no link, and with no profile page
 * wired even an id stays plain.
 *
 * Record: `./gradlew :core:catalog:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class ReviewAuthorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun authorsArabic() = snap(AppLanguage.ARABIC)

    @Test
    fun authorsEnglish() = snap(AppLanguage.ENGLISH)

    @Test
    fun authorsDarkArabic() = snap(AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun authorsDarkEnglish() = snap(AppLanguage.ENGLISH, BahrThemeName.Dark)

    @Test
    fun `a linked name hands over its id - A traveller too - a plain one has no link`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                Column {
                    ReviewCard(review(author = "Karim A."), onAuthorClick = { opened += it })
                    ReviewCard(review(author = "A traveller", publicId = "p-2"), onAuthorClick = { opened += it })
                }
            }
        }
        val links = compose.onAllNodesWithTag(REVIEW_AUTHOR_LINK_TAG)
        links.assertCountEquals(1)
        compose.onNodeWithTag(REVIEW_AUTHOR_LINK_TAG).performClick()
        assertEquals(listOf("p-2"), opened)
        val node = compose.onNodeWithTag(REVIEW_AUTHOR_LINK_TAG).fetchSemanticsNode()
        assertEquals("Open profile", node.config.getOrNull(SemanticsActions.OnClick)?.label)
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun `with no profile page wired - an id stays plain text`() {
        compose.setContent {
            Themed(AppLanguage.ENGLISH) { ReviewCard(review(author = "Mona S.", publicId = "p-1")) }
        }
        compose.onAllNodesWithTag(REVIEW_AUTHOR_LINK_TAG).assertCountEquals(0)
    }

    private fun snap(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        val arabic = language == AppLanguage.ARABIC
        compose.setContent {
            Themed(language, theme) {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(BahrSpacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
                ) {
                    ReviewCard(review(author = if (arabic) "كريم ع." else "Karim A."), onAuthorClick = {})
                    ReviewCard(review(author = if (arabic) "منى س." else "Mona S.", publicId = "p-1"), onAuthorClick = {})
                    ReviewCard(review(author = if (arabic) "مسافر" else "A traveller", publicId = "p-2"), onAuthorClick = {})
                }
            }
        }
        compose.waitForIdle()
        compose.captureScreenshot("review_authors${if (theme == BahrThemeName.Dark) "_dark" else ""}_${language.tag}")
    }

    @Composable
    private fun Themed(
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
        content: @Composable () -> Unit,
    ) {
        ProvideAppLanguage(language) {
            BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English, theme = theme) {
                content()
            }
        }
    }

    private fun review(
        author: String,
        publicId: String? = null,
    ) = ReviewDto(
        author = author,
        authorPublicId = publicId,
        dateISO = LocalDate(2026, 10, 3),
        // Rating only: the name row is what these shots are about.
        body = null,
        rating = 5,
        tone = "teal",
    )
}
