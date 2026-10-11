package eg.bahr.feature.home.presentation

import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.FixedClock
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.core.testing.storedJoin
import eg.bahr.feature.home.data.FakeTripRepository
import eg.bahr.feature.home.data.TripFixtures.page
import eg.bahr.feature.home.data.TripFixtures.trip
import eg.bahr.feature.home.model.HomeDto
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "Waiting list" tag on Home's cards (M4-M5): Home reads the stored joins like every list, and
 * drops the passed ones. Home's half of feature:trips' `WaitlistTagsTest` "every list reads the stored
 * joins", which built Home's view model next to the trip lists' until Home left that feature (M4-M1c).
 */
class WaitlistTagsHomeTest {
    private val saturday = LocalDate.parse("2026-10-24")

    private fun card() = trip(1, nextDeparture = NextDepartureDto(date = saturday, seatsRemaining = 0, capacity = 18, soldOut = true))

    @Test
    fun `Home reads the stored joins - and drops passed ones`() =
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
                )

            val home = TripListViewModel(repository, memory)
            advanceUntilIdle()

            assertEquals(listOf("dep-3"), store.joins.value.map { it.departureId }, "Home pruned the passed date")
            assertTrue(
                home.uiState.value.waitlistTags
                    .shows(card()),
            )

            // A join forgotten elsewhere (the trip page saw seats open up) untags the cards.
            store.remove(setOf("dep-3"))
            advanceUntilIdle()
            assertFalse(
                home.uiState.value.waitlistTags
                    .shows(card()),
            )
        }
}
