package eg.bahr.feature.trips.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import eg.bahr.feature.trips.data.TripQuery
import eg.bahr.feature.trips.model.TripPageDto
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

/**
 * M4-M1b review #1: a filter switch on the category page goes through loading to the new cards or
 * to the error with its retry, and never leaves the previous filter's cards under the new chip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class CategoryFilterSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun saveLocale() {
        deviceLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun `a failed switch shows the error with retry - not the old filter's cards - and retry loads it`() {
        val weekend = ArrayDeque(listOf(CompletableDeferred<AppResult<TripPageDto>>(), CompletableDeferred()))
        val answers = weekend.toList()
        val repository =
            FakeTripRepository(
                listFiltered = { q ->
                    if (q.filter == null) {
                        page(listOf(trip(1, title = ALL_CARD)), facets = facets(category = q.category))
                    } else {
                        weekend.removeFirst().await()
                    }
                },
            )
        val vm = CategoryTripsViewModel("birds", null, repository)
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    CategoryTripsScreen("birds", null, {}, {}, viewModel = vm)
                }
            }
        }
        compose.onNodeWithText(ALL_CARD).assertIsDisplayed()

        compose.onNodeWithText("Weekend").performClick()
        compose.waitForIdle()
        // Still loading: the old filter's card is gone at once.
        compose.onNodeWithText(ALL_CARD).assertDoesNotExist()

        compose.runOnIdle { answers[0].complete(AppResult.Failure(AppError.Network)) }
        compose.waitForIdle()
        compose.onNodeWithText(ALL_CARD).assertDoesNotExist()
        compose.onNodeWithText(RETRY).assertIsDisplayed()

        compose.onNodeWithText(RETRY).performClick()
        compose.runOnIdle { answers[1].complete(page(listOf(trip(2, title = WEEKEND_CARD)), facets = facets(filter = "weekend"))) }
        compose.waitForIdle()
        compose.onNodeWithText(WEEKEND_CARD).assertIsDisplayed()
        assertEquals(
            listOf(TripQuery(0, "birds", null), TripQuery(0, "birds", "weekend"), TripQuery(0, "birds", "weekend")),
            repository.requestedQueries,
            "retry re-runs the failed query, nothing else",
        )
    }

    private companion object {
        const val ALL_CARD = "Every birds trip"
        const val WEEKEND_CARD = "A weekend birds trip"
        const val RETRY = "Try again"
    }
}
