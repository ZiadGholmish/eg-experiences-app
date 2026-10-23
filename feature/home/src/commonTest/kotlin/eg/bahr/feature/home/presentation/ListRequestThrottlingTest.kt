package eg.bahr.feature.home.presentation

import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.home.data.FakeTripRepository
import eg.bahr.feature.home.data.TripFixtures.facets
import eg.bahr.feature.home.data.TripFixtures.page
import eg.bahr.feature.home.data.TripFixtures.trip
import eg.bahr.feature.home.data.waitlistMemory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * M4-M6 (3) on Home: what reaches the server when the user taps chips or Retry quickly. The clock is
 * the test scheduler's virtual one (the retry throttle is a `delay` on it). A presenter stays attached
 * the whole time, as the screen's would, so every query the view model starts really loads. The
 * category page's, search's and "See all"'s are feature:trip-listing's and feature:search's (M4-M1c, M3-M16 split this file).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListRequestThrottlingTest {
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
