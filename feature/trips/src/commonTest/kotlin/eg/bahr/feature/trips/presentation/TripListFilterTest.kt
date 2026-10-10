package eg.bahr.feature.trips.presentation

import androidx.paging.testing.ErrorRecovery
import androidx.paging.testing.LoadErrorHandler
import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.facets
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.TripQuery
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.HomeDto
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Home's filter chips (M4-M1, HANDOFF Home: "Tap filter chip → filters the list in place"). */
class TripListFilterTest {
    /** Answers every query with one trip named after its filter, the chips for that filter and a count of 7. */
    private fun repository() =
        FakeTripRepository(
            listFiltered = { q ->
                page(listOf(trip(q.page, title = "all/${q.filter}")), facets = facets(filter = q.filter), totalItems = 7)
            },
            home = { AppResult.Success(HomeDto()) },
        )

    @Test
    fun `the filter chips come from the list's facets - category facets are not chips - All selected at first`() =
        runViewModelTest {
            val vm = TripListViewModel(repository(), waitlistMemory())

            vm.trips.asSnapshot()

            val state = vm.uiState.value
            assertEquals(listOf("all", "weekend", "under_400", "half_day"), state.filterChips.map { it.key })
            assertEquals(listOf(true, false, false, false), state.filterChips.map(state::isSelected))
            assertEquals(7L, state.totalItems)
            assertTrue(state.listAnswered)
        }

    @Test
    fun `a chip starts the list over with its filter - tapping it again clears it`() =
        runViewModelTest {
            val repository = repository()
            val vm = TripListViewModel(repository, waitlistMemory())
            vm.trips.asSnapshot()

            vm.selectFilter("weekend")
            assertEquals(listOf("all/weekend"), vm.trips.asSnapshot().map { it.title })

            vm.selectFilter("weekend")
            assertNull(vm.uiState.value.filter, "the active chip again clears the filter")
            assertEquals(listOf("all/null"), vm.trips.asSnapshot().map { it.title })
            assertEquals(
                listOf(TripQuery(0), TripQuery(0, filter = "weekend"), TripQuery(0)),
                repository.requestedQueries,
            )
        }

    @Test
    fun `a chip reloads only the list - Home is not read again and the list does not wait for it`() =
        runViewModelTest {
            val repository = repository()
            val vm = TripListViewModel(repository, waitlistMemory())
            vm.trips.asSnapshot()
            advanceUntilIdle()

            vm.selectFilter("half_day")
            vm.trips.asSnapshot()

            assertEquals(1, repository.homeReads)
            assertFalse(vm.uiState.value.awaitingHome)
        }

    @Test
    fun `Retry keeps the filter - and once the list has answered it does not wait for Home again`() =
        runViewModelTest {
            val repository = repository()
            val vm = TripListViewModel(repository, waitlistMemory())
            vm.trips.asSnapshot()
            vm.selectFilter("under_400")
            vm.trips.asSnapshot()
            advanceUntilIdle()

            vm.refresh()
            assertFalse(vm.uiState.value.awaitingHome, "the chips and cards stay; Home's sections reload behind them")
            vm.trips.asSnapshot()
            advanceUntilIdle()

            assertEquals("under_400", vm.uiState.value.filter)
            assertEquals(TripQuery(0, filter = "under_400"), repository.requestedQueries.last())
            assertEquals(2, repository.homeReads)
        }

    @Test
    fun `a new filter forgets the old count until it answers - the chips stay`() =
        runViewModelTest {
            val vm = TripListViewModel(repository(), waitlistMemory())
            vm.trips.asSnapshot()

            vm.selectFilter("weekend")

            assertNull(vm.uiState.value.totalItems)
            assertEquals(4, vm.uiState.value.filterChips.size)
        }

    @Test
    fun `a filter the server refuses falls back to every trip - not an error`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(
                    listFiltered = { q ->
                        if (q.filter != null) {
                            AppResult.Failure(AppError.Api(code = ApiErrorCodes.VALIDATION_FAILED, message = "filter", httpStatus = 400))
                        } else {
                            page(listOf(trip(1)), facets = facets())
                        }
                    },
                    home = { AppResult.Success(HomeDto()) },
                )
            val vm = TripListViewModel(repository, waitlistMemory())
            vm.trips.asSnapshot()

            vm.selectFilter("weekend")
            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })
            val shown = vm.trips.asSnapshot()

            assertNull(vm.uiState.value.filter)
            assertEquals(listOf("trip-1"), shown.map { it.slug })
        }

    @Test
    fun `a failed first page leaves the list unanswered - so the screen shows the whole-screen error`() =
        runViewModelTest {
            val vm =
                TripListViewModel(
                    FakeTripRepository(listTrips = { AppResult.Failure(AppError.Network) }, home = { AppResult.Success(HomeDto()) }),
                    waitlistMemory(),
                )

            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })

            assertFalse(vm.uiState.value.listAnswered)
        }
}
