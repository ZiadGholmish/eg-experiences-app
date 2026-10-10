package eg.bahr.feature.trips.data

import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.sectionPage
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripPageDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The two list endpoints as Paging sources (M4-M1b), driven with paging-testing's [TestPager]. */
class TripPagingSourcesTest {
    @Test
    fun `every page is one fixed size inside the endpoints' 1 to 50 - the first load too`() {
        // Paging's default first load is 3 pages (60), which the server would refuse with a 400.
        assertEquals(TripApiService.PAGE_SIZE, TripPagingConfig.pageSize)
        assertEquals(TripApiService.PAGE_SIZE, TripPagingConfig.initialLoadSize)
        assertEquals(false, TripPagingConfig.enablePlaceholders)
        // Half a page: opening a list does not ask for page 1 before the user scrolls.
        assertEquals(TripApiService.PAGE_SIZE / 2, TripPagingConfig.prefetchDistance)
    }

    @Test
    fun `the list starts at page 0 and appends page by page until the last`() =
        runTest {
            val repository = FakeTripRepository(listTrips = { p -> page(listOf(trip(p)), page = p, totalPages = 2) })
            val pager = TestPager(TripPagingConfig, TripListPagingSource(repository))

            val first = assertIs<LoadResult.Page<Int, TripCardDto>>(pager.refresh())
            assertNull(first.prevKey, "lists always start at the top: nothing is prepended")
            assertEquals(1, first.nextKey)

            val second = assertIs<LoadResult.Page<Int, TripCardDto>>(pager.append())
            assertEquals(listOf("trip-1"), second.data.map { it.slug })
            assertNull(second.nextKey, "page 1 of 2 is the last")
            assertEquals(listOf(0, 1), repository.requestedPages)
        }

    @Test
    fun `an empty page ends the list even when the total says there is more`() =
        runTest {
            val repository = FakeTripRepository(listTrips = { page(emptyList(), totalPages = 5) })

            val result =
                assertIs<LoadResult.Page<Int, TripCardDto>>(TestPager(TripPagingConfig, TripListPagingSource(repository)).refresh())

            assertNull(result.nextKey)
        }

    @Test
    fun `a failed page is a load error carrying the AppError`() =
        runTest {
            val repository = FakeTripRepository(listTrips = { AppResult.Failure(AppError.Timeout) })

            val result =
                assertIs<LoadResult.Error<Int, TripCardDto>>(TestPager(TripPagingConfig, TripListPagingSource(repository)).refresh())

            assertEquals(AppError.Timeout, result.throwable.appError())
        }

    @Test
    fun `the category and filter go with every page - and only page 0 is reported`() =
        runTest {
            val heard = mutableListOf<AppResult<TripPageDto>>()
            val repository = FakeTripRepository(listFiltered = { q -> page(listOf(trip(q.page)), page = q.page, totalPages = 2) })
            val pager = TestPager(TripPagingConfig, TripListPagingSource(repository, "birds", "weekend") { heard += it })

            pager.refresh()
            pager.append()

            assertEquals(listOf(TripQuery(0, "birds", "weekend"), TripQuery(1, "birds", "weekend")), repository.requestedQueries)
            assertEquals(1, heard.size)
        }

    @Test
    fun `a section's list pages by the section id and reports its first page`() =
        runTest {
            var total: Long? = null
            val repository =
                FakeTripRepository(sectionTrips = { _, p -> sectionPage(listOf(trip(p)), page = p, totalPages = 2, totalItems = 21) })
            val pager =
                TestPager(
                    TripPagingConfig,
                    SectionTripsPagingSource(repository, "s-1") {
                        (it as? AppResult.Success)?.let {
                            total =
                                it.data.totalItems
                        }
                    },
                )

            pager.refresh()
            val last = assertIs<LoadResult.Page<Int, TripCardDto>>(pager.append())

            assertNull(last.nextKey)
            assertEquals(listOf("s-1" to 0, "s-1" to 1), repository.sectionRequests)
            assertEquals(21L, total)
        }

    @Test
    fun `a section gone since Home was read is an error page`() =
        runTest {
            val notFound = AppError.Api(code = "NOT_FOUND", message = null, httpStatus = 404)
            val repository = FakeTripRepository(sectionTrips = { _, _ -> AppResult.Failure(notFound) })

            val result = TestPager(TripPagingConfig, SectionTripsPagingSource(repository, "gone")).refresh()

            assertEquals(notFound, assertIs<LoadResult.Error<Int, TripCardDto>>(result).throwable.appError())
        }

    @Test
    fun `the refresh key is always the first page`() =
        runTest {
            val repository = FakeTripRepository(listTrips = { p -> page(listOf(trip(p)), page = p, totalPages = 3) })
            val pager = TestPager(TripPagingConfig, TripListPagingSource(repository))
            pager.refresh()
            pager.append()

            assertNull(TripListPagingSource(repository).getRefreshKey(pager.getPagingState(anchorPosition = 1)))
        }
}
