package eg.bahr.feature.trips.presentation

import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripListViewModelTest {
    private val errors = AppErrorController()

    @Test
    fun `starts loading and then shows the first page`() =
        runViewModelTest {
            val trips = listOf(trip(1), trip(2), trip(3))
            val repository = FakeTripRepository(listTrips = { page(trips) })

            val vm = TripListViewModel(repository, errors)
            assertTrue(vm.uiState.value.isLoading, "nothing has run yet: the first frame is the loading state")

            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals(trips, state.trips)
            assertNull(state.error)
            assertEquals(listOf(0), repository.requestedPages)
        }

    @Test
    fun `a failed first page is screen state`() =
        runViewModelTest {
            val vm = TripListViewModel(FakeTripRepository(listTrips = { AppResult.Failure(AppError.Network) }), errors)

            advanceUntilIdle()

            assertEquals(AppError.Network, vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `load more appends the next page`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(listTrips = { p -> page(listOf(trip(p * 10)), page = p, totalPages = 2) })
            val vm = TripListViewModel(repository, errors)
            advanceUntilIdle()

            vm.loadMore()
            advanceUntilIdle()

            assertEquals(
                listOf("trip-0", "trip-10"),
                vm.uiState.value.trips
                    .map { it.slug },
            )
            assertFalse(vm.uiState.value.hasMore)
            assertEquals(listOf(0, 1), repository.requestedPages)
        }

    @Test
    fun `a failed load more keeps the list and goes to the app error host`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(
                    listTrips = { p ->
                        if (p == 0) page(listOf(trip(1)), totalPages = 2) else AppResult.Failure(AppError.Timeout)
                    },
                )
            val vm = TripListViewModel(repository, errors)
            advanceUntilIdle()

            vm.loadMore()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf("trip-1"), state.trips.map { it.slug })
            assertNull(state.error, "the list stays usable, so the screen must not switch to its error state")
            assertFalse(state.isLoadingMore)
            assertEquals(AppError.Timeout, errors.current.first()?.error)
        }

    @Test
    fun `load more is ignored while the first page is loading`() =
        runViewModelTest {
            val repository = FakeTripRepository(listTrips = { page(listOf(trip(1)), totalPages = 3) })
            val vm = TripListViewModel(repository, errors)

            vm.loadMore()
            runCurrent()

            assertEquals(listOf(0), repository.requestedPages)
        }

    @Test
    fun `a card repeated across pages is shown once`() =
        runViewModelTest {
            // A trip published between two page reads shifts the next page by one.
            val first = page(listOf(trip(1), trip(2)), totalPages = 2)
            val second = page(listOf(trip(2), trip(3)), page = 1, totalPages = 2)
            val repository = FakeTripRepository(listTrips = { p -> if (p == 0) first else second })
            val vm = TripListViewModel(repository, errors)
            advanceUntilIdle()

            vm.loadMore()
            advanceUntilIdle()

            assertEquals(
                listOf("trip-1", "trip-2", "trip-3"),
                vm.uiState.value.trips
                    .map { it.slug },
            )
        }

    @Test
    fun `an empty first page is loaded and is not an error`() =
        runViewModelTest {
            val vm = TripListViewModel(FakeTripRepository(listTrips = { page(emptyList()) }), errors)

            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertTrue(state.trips.isEmpty())
            assertFalse(state.hasMore)
        }

    @Test
    fun `refresh after a failed first page loads it again`() =
        runViewModelTest {
            var fail = true
            val repository =
                FakeTripRepository(listTrips = { if (fail) AppResult.Failure(AppError.Network) else page(listOf(trip(1))) })
            val vm = TripListViewModel(repository, errors)
            advanceUntilIdle()

            fail = false
            vm.refresh()
            assertTrue(vm.uiState.value.isLoading, "retry shows the loading state again")
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertEquals(
                listOf("trip-1"),
                vm.uiState.value.trips
                    .map { it.slug },
            )
            assertEquals(listOf(0, 0), repository.requestedPages)
        }
}
