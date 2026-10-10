package eg.bahr.feature.trips.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import eg.bahr.feature.trips.presentation.components.REFRESH_BAR_TAG
import eg.bahr.feature.trips.presentation.components.REFRESH_SLOT_TAG
import eg.bahr.feature.trips.presentation.components.queryStatus
import eg.bahr.feature.trips.presentation.components.tripRowTag
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
 * A filter switch on the category page (M4-M1b review #1, M4-M6): the previous filter's cards stay on
 * screen while the new filter loads, dimmed, not tappable and under the thin progress bar; then the
 * new cards replace them, or the error with its retry does. The old cards never pass for the new
 * result.
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
    fun `old cards stay dimmed under the progress bar while a switch loads - then the new cards replace them`() {
        val weekend = CompletableDeferred<AppResult<TripPageDto>>()
        val vm = categoryVm { weekend.await() }
        show(vm)
        compose.onNodeWithText(ALL_CARD).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag(REFRESH_BAR_TAG).assertDoesNotExist()

        compose.onNodeWithText("Weekend").performClick()
        compose.waitForIdle()
        // Loading: the old card is still on screen, but stale: it cannot be opened, screen readers do not
        // read it as the result (M4-M6 review #3), and the bar says why.
        assertStale(ALL_SLUG)
        compose.onNodeWithTag(REFRESH_BAR_TAG).assertIsDisplayed()
        compose.onNodeWithText(RETRY).assertDoesNotExist()

        compose.runOnIdle { weekend.complete(page(listOf(trip(2, title = WEEKEND_CARD)), facets = facets(filter = "weekend"))) }
        compose.waitForIdle()
        compose.onNodeWithText(WEEKEND_CARD).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(ALL_CARD).assertDoesNotExist()
        compose.onNodeWithTag(REFRESH_BAR_TAG).assertDoesNotExist()
        assertOpens(WEEKEND_SLUG)
        assertEquals(listOf(WEEKEND_SLUG), opened)
    }

    @Test
    fun `a failed switch replaces the old cards with the error - its retry shows the spinner - then the new cards`() {
        val weekend = ArrayDeque(listOf(CompletableDeferred<AppResult<TripPageDto>>(), CompletableDeferred()))
        val answers = weekend.toList()
        val vm = categoryVm { weekend.removeFirst().await() }
        show(vm)
        compose.onNodeWithText(ALL_CARD).assertIsDisplayed()

        compose.onNodeWithText("Weekend").performClick()
        compose.waitForIdle()
        assertStale(ALL_SLUG)

        compose.runOnIdle { answers[0].complete(AppResult.Failure(AppError.Network)) }
        compose.waitForIdle()
        compose.onNodeWithText(ALL_CARD).assertDoesNotExist()
        compose.onNodeWithTag(REFRESH_BAR_TAG).assertDoesNotExist()
        compose.onNodeWithText(RETRY).assertIsDisplayed()

        compose.onNodeWithText(RETRY).performClick()
        compose.waitForIdle()
        // After an error, its retry is a plain load: the cards from before the error do not come back.
        compose.onNodeWithText(ALL_CARD).assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        compose.onNodeWithTag(REFRESH_BAR_TAG).assertDoesNotExist()

        compose.runOnIdle { answers[1].complete(page(listOf(trip(2, title = WEEKEND_CARD)), facets = facets(filter = "weekend"))) }
        compose.waitForIdle()
        compose.onNodeWithText(WEEKEND_CARD).assertIsDisplayed()
        assertEquals(
            listOf(TripQuery(0, "birds", null), TripQuery(0, "birds", "weekend"), TripQuery(0, "birds", "weekend")),
            repository.requestedQueries,
            "retry re-runs the failed query, nothing else",
        )
    }

    private lateinit var repository: FakeTripRepository

    /** The trips the screen was asked to open, in order. */
    private val opened = mutableListOf<String>()

    /**
     * [slug]'s card is drawn (found by its row's tag) but not tappable and not exposed to accessibility.
     *
     * "Not tappable" is a real touch on the card, not a semantics check (M4-M6 review #10): the row's
     * semantics are cleared, so a "no click action" assertion would pass whether the card reacts or not.
     */
    private fun assertStale(slug: String) {
        compose
            .onNodeWithTag(tripRowTag(slug))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .assert(SemanticsMatcher("has no children for accessibility") { it.children.isEmpty() })
        // The merged tree is what TalkBack and VoiceOver walk. (The unmerged tree still lists the
        // cleared texts as raw layout nodes; that is a test view, not an accessibility one.)
        compose.onNodeWithText(ALL_CARD).assertDoesNotExist()

        val before = opened.toList()
        compose.onNodeWithTag(tripRowTag(slug)).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(before, opened, "a stale card does not open its trip")
        // The screen reader hears that the list is busy (M4-M6 review #8): one slot (the page has one
        // under the header too, for when there are no chips) says so, politely.
        compose
            .onAllNodesWithTag(REFRESH_SLOT_TAG)
            .filter(hasContentDescription(BUSY))
            .assertCountEquals(1)
            .onFirst()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /** The same touch on a current card opens its trip: the control for [assertStale]'s touch. */
    private fun assertOpens(slug: String) {
        val before = opened.size
        compose.onNodeWithTag(tripRowTag(slug)).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(before + 1, opened.size, "a current card opens its trip")
        // Idle again: nothing busy to say.
        compose
            .onAllNodesWithTag(REFRESH_SLOT_TAG)
            .filter(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(0)
    }

    /** The category page over a fake whose unfiltered list is [ALL_CARD] and whose filtered list is [filtered]. */
    private fun categoryVm(filtered: suspend () -> AppResult<TripPageDto>): CategoryTripsViewModel {
        repository =
            FakeTripRepository(
                listFiltered = { q ->
                    if (q.filter == null) page(listOf(trip(1, title = ALL_CARD)), facets = facets(category = q.category)) else filtered()
                },
            )
        return CategoryTripsViewModel("birds", null, repository, waitlistMemory())
    }

    private fun show(vm: CategoryTripsViewModel) {
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    CategoryTripsScreen("birds", null, onBack = {}, onTripClick = { opened += it }, viewModel = vm)
                }
            }
        }
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
                        if (probing && firstFrame.isEmpty()) firstFrame += probe.itemCount to probe.queryStatus(afterFailure = false)
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

        /** [ALL_CARD]'s slug (`TripFixtures.trip(1)`). */
        const val ALL_SLUG = "trip-1"
        const val WEEKEND_CARD = "A weekend birds trip"

        /** [WEEKEND_CARD]'s slug (`TripFixtures.trip(2)`). */
        const val WEEKEND_SLUG = "trip-2"
        const val RETRY = "Try again"

        /** `a11y_busy` in English. */
        const val BUSY = "Loading"
    }
}
