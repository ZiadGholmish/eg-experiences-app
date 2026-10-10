package eg.bahr.feature.trips.presentation

import androidx.paging.testing.asSnapshot
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.sectionPage
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.HomeSeeAllDto
import eg.bahr.feature.trips.model.TripsSectionDto
import eg.bahr.feature.trips.navigation.HomeAction
import eg.bahr.feature.trips.presentation.components.rowTitleSharedKey
import eg.bahr.feature.trips.presentation.components.seeAllAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A Home row's "See all" (M4-M1b): when it shows, where it leads, and the paged list it opens. */
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
    fun `see all shows only when the whole list is longer than the row`() {
        val row = row(totalItems = 2, seeAll = HomeSeeAllDto("section", "s-1"))

        assertNull(row.seeAllAction(), "two of two: nothing more to see")
        assertEquals(HomeAction.OpenSection("s-1", "Featured", rowTitleSharedKey(row.id)), row.copy(totalItems = 3).seeAllAction())
        assertNull(row.copy(totalItems = null).seeAllAction(), "an older server sends no total")
        assertNull(row.copy(totalItems = 3, seeAll = null).seeAllAction(), "nor a target")
    }

    @Test
    fun `a category row's see all opens the category page - an unknown target type shows no link`() {
        val row = row(totalItems = 9, seeAll = HomeSeeAllDto("category", "birds"))

        assertEquals(HomeAction.OpenCategory("birds", "Featured", rowTitleSharedKey(row.id)), row.seeAllAction())
        assertNull(row.copy(seeAll = HomeSeeAllDto("map", "x")).seeAllAction())
    }

    @Test
    fun `an empty page is an empty list`() =
        runViewModelTest {
            val repository = FakeTripRepository(sectionTrips = { _, _ -> sectionPage(emptyList()) })

            val shown = SectionTripsViewModel("s-1", null, repository, waitlistMemory()).trips.asSnapshot()

            assertEquals(emptyList(), shown)
        }

    private fun row(
        totalItems: Int?,
        seeAll: HomeSeeAllDto?,
    ) = TripsSectionDto(
        id = "s-1",
        type = "trips",
        title = "Featured",
        layout = "row",
        items = listOf(trip(1), trip(2)),
        totalItems = totalItems,
        seeAll = seeAll,
    )
}
