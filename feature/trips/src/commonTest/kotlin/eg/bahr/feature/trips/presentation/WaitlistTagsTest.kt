package eg.bahr.feature.trips.presentation

import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.FixedClock
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.core.testing.storedJoin
import eg.bahr.feature.trips.data.FakeRecentSearchesStore
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.sectionPage
import eg.bahr.feature.trips.data.TripFixtures.trip
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "Waiting list" tag on list cards (M4-M5): every trip list reads the stored joins. When a card
 * shows the tag is core:catalog's `WaitlistTagsTest` (moved there, M4-M7).
 */
class WaitlistTagsTest {
    private val saturday = LocalDate.parse("2026-10-24")

    private fun card() = trip(1, nextDeparture = NextDepartureDto(date = saturday, seatsRemaining = 0, capacity = 18, soldOut = true))

    @Test
    fun `every list reads the stored joins`() =
        runViewModelTest {
            // Home's half (it also drops passed joins) is feature:home's `WaitlistTagsHomeTest` (M4-M1c).
            val store =
                FakeWaitlistJoinsStore(
                    listOf(
                        storedJoin(tripSlug = "trip-1"),
                        storedJoin(departureId = "old", tripSlug = "trip-2", date = "2026-10-03"),
                    ),
                )
            val memory = WaitlistMemory(store, FixedClock())
            val repository =
                FakeTripRepository(
                    listTrips = { page(listOf(card())) },
                    sectionTrips = { _, _ -> sectionPage(listOf(card())) },
                )

            val category = CategoryTripsViewModel("birds", null, repository, memory)
            val section = SectionTripsViewModel("s-1", null, repository, memory)
            val search = SearchTripsViewModel(repository, FakeRecentSearchesStore(), memory)
            advanceUntilIdle()

            listOf(
                category.uiState.value.waitlistTags,
                section.uiState.value.waitlistTags,
                search.uiState.value.waitlistTags,
            ).forEach { assertTrue(it.shows(card())) }

            // A join forgotten elsewhere (the trip page saw seats open up) untags the cards.
            store.remove(setOf("dep-3"))
            advanceUntilIdle()
            assertFalse(
                search.uiState.value.waitlistTags
                    .shows(card()),
            )
        }
}
