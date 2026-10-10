package eg.bahr.feature.trips.presentation

import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeRecentSearchesStore
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.facets
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.sectionPage
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.TripQuery
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.TripPageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
 * model starts really loads, and a test would see a request a burst should not have sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListRequestThrottlingTest {
    @Test
    fun `the same search is not sent again - outer spaces or the search key change nothing`() =
        runViewModelTest {
            val repository = FakeTripRepository(listFiltered = { q -> page(listOf(trip(1, title = "${q.q}"))) })
            val vm = SearchTripsViewModel(repository, FakeRecentSearchesStore(), waitlistMemory())
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
    fun `rapid chip taps - each shows at once - collapse to one request for the last`() =
        runViewModelTest {
            val answers = mutableMapOf<String?, CompletableDeferred<AppResult<TripPageDto>>>()
            val cancelled = mutableListOf<String?>()
            val repository =
                FakeTripRepository(
                    listFiltered = { q ->
                        val answer = answers.getOrPut(q.filter) { CompletableDeferred() }
                        try {
                            answer.await()
                        } catch (stopped: CancellationException) {
                            cancelled += q.filter
                            throw stopped
                        }
                    },
                )
            val vm = CategoryTripsViewModel("birds", null, repository, waitlistMemory())
            present(vm.trips)
            answers.getValue(null).complete(page(listOf(trip(1)), facets = facets(category = "birds")))
            runCurrent()

            // Three taps within one frame: each is the selected chip at once, one request goes.
            vm.selectFilter("weekend")
            assertEquals("weekend", vm.uiState.value.filter, "the chip is selected at once, before any answer")
            vm.selectFilter("under_400")
            vm.selectFilter("half_day")
            runCurrent()
            assertEquals(listOf(null, "half_day"), repository.requestedQueries.map { it.filter })

            // Taps a frame apart: the request of each one before the last is cancelled, so only the last answers.
            vm.selectFilter("weekend")
            runCurrent()
            vm.selectFilter("under_400")
            runCurrent()
            assertEquals<List<String?>>(listOf("half_day", "weekend"), cancelled, "a request in flight is cancelled by the next tap")
            answers.getValue("under_400").complete(page(listOf(trip(2)), facets = facets(category = "birds", filter = "under_400")))
            advanceUntilIdle()

            assertEquals(listOf(null, "half_day", "weekend", "under_400"), repository.requestedQueries.map { it.filter })
            assertEquals("under_400", vm.uiState.value.filter)
            assertEquals(1L, vm.uiState.value.totalItems, "only the last tap's answer lands")
        }

    @Test
    fun `a burst of Retry on the category page is one request - a retry after the window is another`() =
        runViewModelTest {
            var failing = true
            val repository =
                FakeTripRepository(
                    listFiltered = { if (failing) AppResult.Failure(AppError.Network) else page(listOf(trip(1))) },
                )
            val vm = CategoryTripsViewModel("birds", null, repository, waitlistMemory())
            present(vm.trips)
            runCurrent()
            assertEquals(1, repository.requestedQueries.size)

            repeat(BURST) {
                vm.retry()
                runCurrent()
            }
            assertEquals(2, repository.requestedQueries.size, "the first tap reloads; the rest of the burst sends nothing")

            advanceTimeBy(RETRY_THROTTLE_MILLIS + 1)
            failing = false
            vm.retry()
            runCurrent()
            assertEquals(3, repository.requestedQueries.size, "a deliberate retry after the window goes")
        }

    @Test
    fun `a burst of Retry in search is one request`() =
        runViewModelTest {
            val repository = FakeTripRepository(listFiltered = { AppResult.Failure(AppError.Network) })
            val vm = SearchTripsViewModel(repository, FakeRecentSearchesStore(), waitlistMemory())
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

    @Test
    fun `a burst of Retry on a See all list is one request`() =
        runViewModelTest {
            val repository = FakeTripRepository(sectionTrips = { _, _ -> AppResult.Failure(AppError.Network) })
            val vm = SectionTripsViewModel("s-1", "Featured", repository, waitlistMemory())
            present(vm.trips)
            runCurrent()

            repeat(BURST) {
                vm.retry()
                runCurrent()
            }
            assertEquals(listOf("s-1" to 0, "s-1" to 0), repository.sectionRequests)

            advanceTimeBy(RETRY_THROTTLE_MILLIS + 1)
            repository.sectionTrips = { _, _ -> sectionPage(listOf(trip(1))) }
            vm.retry()
            runCurrent()
            assertEquals(3, repository.sectionRequests.size)
        }

    @Test
    fun `a burst of Retry on Home is one reload`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(
                    listTrips = { AppResult.Failure(AppError.Network) },
                    home = { AppResult.Failure(AppError.Network) },
                )
            val vm = TripListViewModel(repository, waitlistMemory())
            present(vm.trips)
            runCurrent()
            assertEquals(1, repository.homeReads)

            repeat(BURST) {
                vm.refresh()
                runCurrent()
            }

            assertEquals(2, repository.homeReads)
            assertEquals(listOf(0, 0), repository.requestedPages)
        }

    @Test
    fun `rapid chip taps on Home collapse to one request for the last - and Home is not read again`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(
                    listFiltered = { q -> page(listOf(trip(1)), facets = facets(filter = q.filter)) },
                    home = { AppResult.Failure(AppError.Network) },
                )
            val vm = TripListViewModel(repository, waitlistMemory())
            present(vm.trips)
            advanceUntilIdle()

            vm.selectFilter("weekend")
            vm.selectFilter("under_400")
            vm.selectFilter("half_day")
            advanceUntilIdle()

            assertEquals(listOf(null, "half_day"), repository.requestedQueries.map { it.filter })
            assertEquals(1, repository.homeReads)
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
