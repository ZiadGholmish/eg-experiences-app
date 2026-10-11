package eg.bahr.feature.trips.presentation

import androidx.paging.testing.asSnapshot
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.sectionPage
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A Home row's "See all" (M4-M1b): the paged list it opens. When the link shows and where it leads
 * are feature:home's (`SeeAllActionTest`, M4-M1c).
 */
class SectionTripsViewModelTest {
    @Test
    fun `see all lists the section's trips page by page - with the row's title and the total`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(sectionTrips = { _, p -> sectionPage(listOf(trip(p)), page = p, totalPages = 2, totalItems = 2) })
            val vm = SectionTripsViewModel("s-1", "Featured trips", repository, waitlistMemory())

            val shown = vm.trips.asSnapshot { appendScrollWhile { true } }

            assertEquals(listOf("trip-0", "trip-1"), shown.map { it.slug })
            assertEquals(listOf("s-1" to 0, "s-1" to 1), repository.sectionRequests)
            assertEquals("Featured trips", vm.uiState.value.title)
            assertEquals(2L, vm.uiState.value.totalItems)
        }

    @Test
    fun `an empty page is an empty list`() =
        runViewModelTest {
            val repository = FakeTripRepository(sectionTrips = { _, _ -> sectionPage(emptyList()) })

            val shown = SectionTripsViewModel("s-1", null, repository, waitlistMemory()).trips.asSnapshot()

            assertEquals(emptyList(), shown)
        }
}
