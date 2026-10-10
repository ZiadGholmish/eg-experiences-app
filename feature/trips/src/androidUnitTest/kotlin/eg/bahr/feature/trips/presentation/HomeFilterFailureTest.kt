package eg.bahr.feature.trips.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.facets
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.HomeDto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNull

/**
 * M4-M1 review #2: once the list has answered, a filter whose reload fails shows its error and Retry
 * inside the page, under the chips (never the whole-screen error), so the filter can be changed back:
 * "All trips" brings the list back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h1400dp-xhdpi")
class HomeFilterFailureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a failed filter reload keeps the chips with an inline Retry - All recovers the list`() {
        val repository =
            FakeTripRepository(
                listFiltered = { q ->
                    if (q.filter == "weekend") {
                        AppResult.Failure(AppError.Network)
                    } else {
                        page(listOf(trip(1, title = "Dawn on Lake Burullus")), facets = facets(filter = q.filter))
                    }
                },
                home = { AppResult.Success(HomeDto()) },
            )
        val viewModel = TripListViewModel(repository, waitlistMemory())
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    TripListScreen(onTripClick = {}, viewModel = viewModel)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Dawn on Lake Burullus").assertIsDisplayed()

        compose.onNodeWithText("Weekend").performClick()
        compose.waitForIdle()

        // The page is still Home: the chips and the list's own error with its Retry, in the list.
        compose.onNode(hasScrollToKeyAction()).assertExists()
        compose.onNodeWithText("Weekend").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("Dawn on Lake Burullus").assertDoesNotExist()

        // The "All trips" chip, the first of the two "All trips" (the heading under the chips is the other).
        compose.onAllNodesWithText("All trips").onFirst().performClick()
        compose.waitForIdle()

        assertNull(viewModel.uiState.value.filter)
        compose.onNodeWithText("Dawn on Lake Burullus").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }
}
