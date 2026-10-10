package eg.bahr.feature.trips.presentation

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeRecentSearchesStore
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.FakeWaitlistJoinsStore
import eg.bahr.feature.trips.data.FixedClock
import eg.bahr.feature.trips.data.JoinedWaitlist
import eg.bahr.feature.trips.data.TripFixtures.page
import eg.bahr.feature.trips.data.TripFixtures.sectionPage
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.data.storedJoin
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.NextDepartureDto
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The "Waiting list" tag on list cards (M4-M5): when a card shows it, and that every list reads it. */
class WaitlistTagsTest {
    private val saturday = LocalDate.parse("2026-10-24")

    private fun joined(
        slug: String = "trip-1",
        date: LocalDate = saturday,
    ) = WaitlistTags.of(listOf(JoinedWaitlist("dep-3", slug, date, partySize = 2, phone = "+201001234567")))

    private fun card(
        date: LocalDate? = saturday,
        soldOut: Boolean? = true,
    ) = trip(1, nextDeparture = NextDepartureDto(date = date, seatsRemaining = 0, capacity = 18, soldOut = soldOut))

    @Test
    fun `a sold-out next departure the device joined is tagged`() {
        assertTrue(joined().shows(card()))
    }

    @Test
    fun `not tagged when the next departure has seats - or no sold-out flag`() {
        assertFalse(joined().shows(card(soldOut = false)))
        assertFalse(joined().shows(card(soldOut = null)))
    }

    @Test
    fun `not tagged for a join on another date or another trip`() {
        assertFalse(joined(date = LocalDate.parse("2026-10-31")).shows(card()))
        assertFalse(joined(slug = "trip-2").shows(card()))
    }

    @Test
    fun `not tagged without a next departure or its date`() {
        assertFalse(joined().shows(trip(1)))
        assertFalse(joined().shows(card(date = null)))
        assertFalse(WaitlistTags.None.shows(card()))
    }

    @Test
    fun `every list reads the stored joins - and Home drops passed ones`() =
        runViewModelTest {
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
                    home = { AppResult.Success(HomeDto(emptyList())) },
                    listTrips = { page(listOf(card())) },
                    sectionTrips = { _, _ -> sectionPage(listOf(card())) },
                )

            val home = TripListViewModel(repository, memory)
            val category = CategoryTripsViewModel("birds", null, repository, memory)
            val section = SectionTripsViewModel("s-1", null, repository, memory)
            val search = SearchTripsViewModel(repository, FakeRecentSearchesStore(), memory)
            advanceUntilIdle()

            assertEquals(listOf("dep-3"), store.joins.value.map { it.departureId }, "Home pruned the passed date")
            listOf(
                home.uiState.value.waitlistTags,
                category.uiState.value.waitlistTags,
                section.uiState.value.waitlistTags,
                search.uiState.value.waitlistTags,
            ).forEach { assertTrue(it.shows(card())) }

            // A join forgotten elsewhere (the trip page saw seats open up) untags the cards.
            store.remove(setOf("dep-3"))
            advanceUntilIdle()
            assertFalse(
                home.uiState.value.waitlistTags
                    .shows(card()),
            )
            assertFalse(
                search.uiState.value.waitlistTags
                    .shows(card()),
            )
        }
}
