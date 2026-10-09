package eg.bahr.feature.trips.presentation

import eg.bahr.feature.trips.data.TripFixtures.saturdays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D3: a date's state comes from `unavailableReason`, never from `soldOut`. */
class DateAvailabilityTest {
    private val open = saturdays().first()

    @Test
    fun `each contract reason maps to its own state`() {
        assertEquals(DateAvailability.Open, open.availability)
        assertEquals(DateAvailability.SoldOut, open.copy(bookable = false, unavailableReason = "SOLD_OUT").availability)
        assertEquals(DateAvailability.Cancelled, open.copy(bookable = false, unavailableReason = "CANCELLED").availability)
        assertEquals(DateAvailability.Closed, open.copy(bookable = false, unavailableReason = "CLOSED").availability)
    }

    @Test
    fun `a cancelled date that was also full reads cancelled and cannot be picked`() {
        val cancelledFull = open.copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "CANCELLED")

        assertEquals(DateAvailability.Cancelled, cancelledFull.availability)
        assertFalse(cancelledFull.isSelectable())
    }

    @Test
    fun `a reason this build does not know is not bookable`() {
        // The contract may add a reason; it must never be offered for booking, even if `bookable` lied.
        val future = open.copy(bookable = true, unavailableReason = "WEATHER")

        assertEquals(DateAvailability.Closed, future.availability)
        assertFalse(future.isSelectable())
    }

    @Test
    fun `only open and sold-out dates can be picked`() {
        assertTrue(open.isSelectable())
        assertTrue(open.copy(soldOut = true, bookable = false, unavailableReason = "SOLD_OUT").isSelectable())
        assertFalse(open.copy(soldOut = true, bookable = false, unavailableReason = "CLOSED").isSelectable())
    }
}
