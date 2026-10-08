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
            val trips = listOf(trip(1, category = "Birding"), trip(2, category = "Sailing"), trip(3, category = "Birding"))
            val repository = FakeTripRepository(listTrips = { page(trips) })

            val vm = TripListViewModel(repository, errors)
            assertTrue(vm.uiState.value.isLoading, "nothing has run yet: the first frame is the loading state")

            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals(trips, state.trips)
            assertEquals(listOf("Birding", "Sailing"), state.categories)
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
                FakeTripRepository(listTrips = { p -> page(listOf(trip(p * 10L)), page = p, totalPages = 2) })
            val vm = TripListViewModel(repository, errors)
            advanceUntilIdle()

            vm.loadMore()
            advanceUntilIdle()

            assertEquals(
                listOf(0L, 10L),
                vm.uiState.value.trips
                    .map { it.id },
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
            assertEquals(listOf(1L), state.trips.map { it.id })
            assertNull(state.error, "the list stays usable, so the screen must not switch to its error state")
            assertFalse(state.isLoadingMore)
            assertEquals(AppError.Timeout, errors.errors.first())
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
    fun `selecting a category filters the visible trips only`() =
        runViewModelTest {
            val trips = listOf(trip(1, category = "Birding"), trip(2, category = "Sailing"))
            val vm = TripListViewModel(FakeTripRepository(listTrips = { page(trips) }), errors)
            advanceUntilIdle()

            vm.selectCategory("Sailing")

            assertEquals(
                listOf(2L),
                vm.uiState.value.visibleTrips
                    .map { it.id },
            )
            assertEquals(trips, vm.uiState.value.trips)
        }
}
