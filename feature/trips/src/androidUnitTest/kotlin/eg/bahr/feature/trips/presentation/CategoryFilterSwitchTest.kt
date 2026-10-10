package eg.bahr.feature.trips.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.compose.collectAsLazyPagingItems
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
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.TripPageDto
import eg.bahr.feature.trips.presentation.components.PagedListStatus
import eg.bahr.feature.trips.presentation.components.queryStatus
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
        val vm = CategoryTripsViewModel("birds", null, repository, waitlistMemory())
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
        // Still loading: the old filter's card is gone at once, and the spinner says why (M4-M1b review S2).
        compose.onNodeWithText(ALL_CARD).assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()

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

    /**
     * M4-M1b review S3: coming back to a list whose pages are cached shows its cards on the first
     * frame, not a spinner and not a second request. This rests on Paging seeding a new presenter from
     * `cachedIn`'s last snapshot, load states included; a Paging upgrade that stopped doing so would
     * show a spinner (and move Home's scroll position), so it is pinned here rather than trusted.
     *
     * The probe reads the same flow the screen does, in the first composition after the screen comes
     * back, before any effect has run: whatever it sees there is what the first frame draws.
     */
    @Test
    fun `a cached list comes back with its cards on the first frame`() {
        val repository =
            FakeTripRepository(listFiltered = { q -> page(listOf(trip(1, title = ALL_CARD)), facets = facets(category = q.category)) })
        val vm = CategoryTripsViewModel("birds", null, repository, waitlistMemory())
        var shown by mutableStateOf(true)
        var probing = false
        val firstFrame = mutableListOf<Pair<Int, PagedListStatus>>()
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    if (shown) {
                        val probe = vm.trips.collectAsLazyPagingItems()
                        if (probing && firstFrame.isEmpty()) firstFrame += probe.itemCount to probe.queryStatus
                        CategoryTripsScreen("birds", null, {}, {}, viewModel = vm)
                    }
                }
            }
        }
        compose.onNodeWithText(ALL_CARD).assertIsDisplayed()

        compose.runOnIdle { shown = false }
        compose.onNodeWithText(ALL_CARD).assertDoesNotExist()
        compose.runOnIdle {
            probing = true
            shown = true
        }
        compose.waitForIdle()

        assertEquals<List<Pair<Int, PagedListStatus>>>(
            listOf(1 to PagedListStatus.Loaded),
            firstFrame,
            "cards, settled, on the first composition back",
        )
        compose.onNodeWithText(ALL_CARD).assertIsDisplayed()
        assertEquals(listOf(TripQuery(0, "birds", null)), repository.requestedQueries, "no second read for a cached list")
    }

    private companion object {
        const val ALL_CARD = "Every birds trip"
        const val WEEKEND_CARD = "A weekend birds trip"
        const val RETRY = "Try again"
    }
}
