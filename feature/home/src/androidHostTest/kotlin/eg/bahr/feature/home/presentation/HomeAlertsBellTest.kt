package eg.bahr.feature.home.presentation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.feature.home.data.FakeTripRepository
import eg.bahr.feature.home.data.waitlistMemory
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * M3-M6a: Home's Alerts bell (the Alerts tab before). A tap goes to the app's `onAlerts`; the count is
 * said in the bell's label ("Notifications, 3 unread", 9+ as "more than 9"), the pill itself is silent,
 * and a new count from the flow reaches the bell without Home being rebuilt. Without `onAlerts` there is
 * no bell.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class HomeAlertsBellTest {
    @get:Rule
    val compose = createComposeRule()

    private val unread = MutableStateFlow<Int?>(null)

    private fun home(onAlerts: (() -> Unit)?) {
        // The list never answers: the app bar shows over the skeleton, and nothing else moves.
        val viewModel =
            TripListViewModel(FakeTripRepository(listTrips = { awaitCancellation() }, home = { awaitCancellation() }), waitlistMemory())
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    TripListScreen(onTripClick = {}, onAlerts = onAlerts, unreadAlerts = unread, viewModel = viewModel)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a tap on the bell opens Alerts`() {
        var taps = 0
        home(onAlerts = { taps++ })

        compose.onNodeWithContentDescription("Notifications").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `the count is read in the bell's label - the pill is silent - and follows the flow`() {
        home(onAlerts = {})

        compose.runOnUiThread { unread.value = 3 }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Notifications, 3 unread").assertExists()
        compose.onAllNodesWithText("3").assertCountEquals(0)

        compose.runOnUiThread { unread.value = 12 }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Notifications, more than 9 unread").assertExists()
        compose.onAllNodesWithText("9+").assertCountEquals(0)

        compose.runOnUiThread { unread.value = 0 }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Notifications").assertExists()
    }

    /** M3-M6a review #1: the 44dp disc sits in a 48dp touch target (handoff: touch targets >= 48dp). */
    @Test
    fun `the bell's touch target is at least 48dp`() {
        home(onAlerts = {})

        compose
            .onNodeWithTag(HOME_ALERTS_TAG)
            .assertWidthIsAtLeast(BahrSize.touchTarget)
            .assertHeightIsAtLeast(BahrSize.touchTarget)
    }

    @Test
    fun `no onAlerts - no bell`() {
        home(onAlerts = null)

        compose.onNodeWithTag(HOME_ALERTS_TAG).assertDoesNotExist()
    }
}
