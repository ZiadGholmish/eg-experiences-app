package eg.bahr.feature.notifications.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.auth.SessionState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** M3-M6a: the page is pushed from Home's bell, and its top bar's Back (read as "Back") calls onBack. */
@RunWith(RobolectricTestRunner::class)
class NotificationsBackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Back calls onBack`() {
        var backs = 0
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    NotificationsContent(NotificationsUiState(SessionState.Unknown), onBack = { backs++ }, signInButtons = {})
                }
            }
        }

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }
}
