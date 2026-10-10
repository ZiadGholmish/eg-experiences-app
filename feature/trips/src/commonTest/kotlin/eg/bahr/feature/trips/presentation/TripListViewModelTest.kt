package eg.bahr.feature.trips.presentation

import androidx.paging.testing.ErrorRecovery
import androidx.paging.testing.LoadErrorHandler
import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Home's "All trips", paged with AndroidX Paging (M4-M1b), read through paging-testing's `asSnapshot`. */
class TripListViewModelTest {
    @Test
    fun `the first page is page 0`() =
        runViewModelTest {
            val trips = listOf(trip(1), trip(2), trip(3))
            val repository = FakeTripRepository(listTrips = { page(trips) })

            val shown = TripListViewModel(repository, waitlistMemory()).trips.asSnapshot()

            assertEquals(trips, shown)
            assertEquals(listOf(0), repository.requestedPages)
        }

    @Test
    fun `scrolling to the end appends the next pages until the last`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(listTrips = { p -> page(List(PAGE) { i -> trip(p * PAGE + i) }, page = p, totalPages = 3) })

            val shown = TripListViewModel(repository, waitlistMemory()).trips.asSnapshot { appendScrollWhile { true } }

            assertEquals(3 * PAGE, shown.size)
            assertEquals(listOf(0, 1, 2), repository.requestedPages)
        }

    @Test
    fun `a card repeated across pages is shown once`() =
        runViewModelTest {
            // A trip published between two page reads shifts the next page by one.
            val first = page(listOf(trip(1), trip(2)), totalPages = 2)
            val second = page(listOf(trip(2), trip(3)), page = 1, totalPages = 2)
            val repository = FakeTripRepository(listTrips = { p -> if (p == 0) first else second })

            val shown = TripListViewModel(repository, waitlistMemory()).trips.asSnapshot { appendScrollWhile { true } }

            assertEquals(listOf("trip-1", "trip-2", "trip-3"), shown.map { it.slug })
        }

    @Test
    fun `a failed next page keeps the pages already shown - and a retry loads just that page`() =
        runViewModelTest {
            var failures = 1
            val repository =
                FakeTripRepository(
                    listTrips = { p ->
                        when {
                            p == 0 -> page(listOf(trip(1)), totalPages = 2)
                            failures-- > 0 -> AppResult.Failure(AppError.Timeout)
                            else -> page(listOf(trip(2)), page = 1, totalPages = 2)
                        }
                    },
                )

            // RETRY is what the list's footer does (Paging never re-asks for a failed page by itself).
            val shown =
                TripListViewModel(repository, waitlistMemory()).trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETRY }) {
                    appendScrollWhile { true }
                }

            assertEquals(listOf("trip-1", "trip-2"), shown.map { it.slug })
            assertEquals(listOf(0, 1, 1), repository.requestedPages, "page 0 is not read again")
        }

    @Test
    fun `an empty first page is an empty list - not an error`() =
        runViewModelTest {
            val shown = TripListViewModel(FakeTripRepository(listTrips = { page(emptyList()) }), waitlistMemory()).trips.asSnapshot()

            assertTrue(shown.isEmpty())
        }

    @Test
    fun `refresh after a failed first page loads page 0 again`() =
        runViewModelTest {
            var fail = true
            val repository =
                FakeTripRepository(listTrips = { if (fail) AppResult.Failure(AppError.Network) else page(listOf(trip(1))) })
            val vm = TripListViewModel(repository, waitlistMemory())
            vm.trips.asSnapshot(onError = LoadErrorHandler { ErrorRecovery.RETURN_CURRENT_SNAPSHOT })

            fail = false
            vm.refresh()
            val shown = vm.trips.asSnapshot()

            assertEquals(listOf("trip-1"), shown.map { it.slug })
            assertEquals(listOf(0, 0), repository.requestedPages)
        }

    private companion object {
        const val PAGE = 20
    }
}
