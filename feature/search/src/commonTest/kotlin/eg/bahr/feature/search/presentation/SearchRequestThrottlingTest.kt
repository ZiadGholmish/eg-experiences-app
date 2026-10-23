package eg.bahr.feature.search.presentation

import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.search.data.FakeRecentSearchesStore
import eg.bahr.feature.search.data.FakeTripListRepository
import eg.bahr.feature.search.data.LastSearch
import eg.bahr.feature.search.data.TripFixtures.page
import eg.bahr.feature.search.data.TripFixtures.trip
import eg.bahr.feature.search.data.TripQuery
import eg.bahr.feature.search.data.waitlistMemory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * M4-M6 (3): what reaches the server when the user types, taps chips or taps Retry quickly. The
 * clock is the test scheduler's virtual one (the debounce and the retry throttle are `delay`s on
 * it). A presenter stays attached the whole time, as the screen's would, so every query the view
 * model starts really loads, and a test would see a request a burst should not have sent. The category and "See all"
 * lists' half is feature:trip-listing's `ListRequestThrottlingTest` (split from feature:trips, M3-M16).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchRequestThrottlingTest {
    @Test
    fun `the same search is not sent again - outer spaces or the search key change nothing`() =
        runViewModelTest {
            val repository = FakeTripListRepository(listFiltered = { q -> page(listOf(trip(1, title = "${q.q}"))) })
            val vm = SearchTripsViewModel(repository, FakeRecentSearchesStore(), waitlistMemory(), LastSearch())
            present(vm.trips)

            vm.onTextChange("felucca")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            runCurrent()
            vm.onTextChange("felucca ")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            runCurrent()
            vm.search()
            advanceUntilIdle()

            assertEquals(listOf(TripQuery(0, q = "felucca")), repository.requestedQueries)
        }

    @Test
    fun `a burst of Retry in search is one request`() =
        runViewModelTest {
            val repository = FakeTripListRepository(listFiltered = { AppResult.Failure(AppError.Network) })
            val vm = SearchTripsViewModel(repository, FakeRecentSearchesStore(), waitlistMemory(), LastSearch())
            present(vm.trips)
            vm.search() // Nothing typed: nothing to search.
            vm.onTextChange("birds")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            runCurrent()
            assertEquals(1, repository.requestedQueries.size)

            repeat(BURST) {
                vm.retry()
                runCurrent()
            }

            assertEquals(listOf("birds", "birds"), repository.requestedQueries.map { it.q })
        }

    /** A presenter on [trips] for the rest of the test, as `collectAsLazyPagingItems` would be. */
    private fun <T : Any> TestScope.present(trips: Flow<PagingData<T>>) {
        val presenter =
            object : PagingDataPresenter<T>() {
                override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) = Unit
            }
        backgroundScope.launch { trips.collectLatest(presenter::collectFrom) }
        runCurrent()
    }

    private companion object {
        /** Taps in a frustrated burst. */
        const val BURST = 5
    }
}
