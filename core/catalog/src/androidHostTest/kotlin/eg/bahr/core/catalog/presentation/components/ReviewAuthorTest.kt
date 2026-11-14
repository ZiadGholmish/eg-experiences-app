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
 * MP-M2/MP-M3: a review's author name × {ar, en} × {light, dark}: a private author (no `authorPublicId`), a public one,
 * and a public one that reads "A traveller" / "مسافر" (an account with no name and a public profile, MP-B2 round 1).
 * Since MP-M3 all three are the same link (in primary, a 44dp touch area). Then the taps: every name is a button with
 * "Open <name>’s profile", the public one hands over its id and the private one null; with no profile page wired, a
 * name stays plain.
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
    fun `every name is a link - a public one hands over its id - a private one null - same role and label`() {
        val opened = mutableListOf<String?>()
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                Column {
                    ReviewCard(review(author = "A traveller"), onAuthorClick = { opened += it })
                    ReviewCard(review(author = "Mona S.", publicId = "p-2"), onAuthorClick = { opened += it })
                }
            }
        }
        val links = compose.onAllNodesWithTag(REVIEW_AUTHOR_LINK_TAG)
        links.assertCountEquals(2)
        links[0].performClick()
        links[1].performClick()
        assertEquals(listOf(null, "p-2"), opened)
        listOf(0 to "A traveller", 1 to "Mona S.").forEach { (index, name) ->
            val node = links[index].fetchSemanticsNode()
            assertEquals("Open $name’s profile", node.config.getOrNull(SemanticsActions.OnClick)?.label)
            assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        }
    }

    @Test
    fun `the Arabic label names the author`() {
        compose.setContent {
            Themed(AppLanguage.ARABIC) { ReviewCard(review(author = "مسافر"), onAuthorClick = {}) }
        }
        val node = compose.onNodeWithTag(REVIEW_AUTHOR_LINK_TAG).fetchSemanticsNode()
        assertEquals("افتح بروفايل مسافر", node.config.getOrNull(SemanticsActions.OnClick)?.label)
    }

    @Test
    fun `with no profile page wired - a name stays plain text, id or not`() {
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                Column {
                    ReviewCard(review(author = "Mona S.", publicId = "p-1"))
                    ReviewCard(review(author = "A traveller"))
                }
            }
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
                    ReviewCard(review(author = if (arabic) "مسافر" else "A traveller"), onAuthorClick = {})
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
