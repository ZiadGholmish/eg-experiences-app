package eg.bahr.core.designsystem.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The Alerts badge for screen readers (M3-M0b, handoff §1): the pill is hidden, and the item says the
 * count once inside its label, "Alerts, 3 unread", with 9+ read as "more than 9". A tab without a count
 * says its name only.
 */
@RunWith(RobolectricTestRunner::class)
class BahrNavigationBarSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun bar(
        count: Int?,
        language: AppLanguage = AppLanguage.ENGLISH,
        onSelect: (Int) -> Unit = {},
    ) = compose.setContent {
        ProvideAppLanguage(language) {
            BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                BahrNavigationBar(
                    props =
                        BahrNavigationBar.Props(
                            items =
                                listOf(
                                    BahrNavigationBar.Item(HOME, BahrIcons.Home, selected = true, testTag = "home"),
                                    BahrNavigationBar.Item(
                                        ALERTS,
                                        BahrIcons.Notifications,
                                        selected = false,
                                        testTag = "alerts",
                                        badgeCount = count,
                                    ),
                                ),
                            onSelect = onSelect,
                        ),
                )
            }
        }
    }

    @Test
    fun `a count is said in the item's label and the pill is silent`() {
        bar(count = 3)

        compose.onNodeWithContentDescription("Alerts, 3 unread").assertExists()
        // Neither the pill's "3" nor a second "Alerts" reaches the merged tree: the item is read once.
        compose.onAllNodesWithText("3").assertCountEquals(0)
        compose.onAllNodesWithText(ALERTS).assertCountEquals(0)
    }

    @Test
    fun `more than nine shows 9+ and is read as more than 9`() {
        bar(count = 12)

        compose.onNodeWithContentDescription("Alerts, more than 9 unread").assertExists()
        compose.onAllNodesWithText("9+").assertCountEquals(0)
    }

    @Test
    fun `no count, or zero, is no badge and just the name`() {
        bar(count = 0)

        compose.onNodeWithContentDescription(ALERTS).assertExists()
        assertEquals(null, BahrNavigationBar.badgeText(0))
        assertEquals(null, BahrNavigationBar.badgeText(null))
        assertEquals("1", BahrNavigationBar.badgeText(1))
        assertEquals("9", BahrNavigationBar.badgeText(9))
        assertEquals("9+", BahrNavigationBar.badgeText(10))
    }

    @Test
    fun `in Arabic the count is said in Arabic`() {
        bar(count = 12, language = AppLanguage.ARABIC)

        compose.onNodeWithContentDescription("$ALERTS، أكتر من 9 جديدة").assertExists()
    }

    @Test
    fun `a tap on the badged item still selects it`() {
        var tapped: Int? = null
        bar(count = 3, onSelect = { tapped = it })

        compose.onNodeWithTag("alerts").performClick()

        assertEquals(1, tapped)
    }

    private companion object {
        const val HOME = "Home"
        const val ALERTS = "Alerts"
    }
}
