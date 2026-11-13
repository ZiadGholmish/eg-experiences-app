package eg.bahr.feature.booking.presentation

import eg.bahr.feature.booking.data.BookingFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

/** MV-M5: the ticket's header and money row follow the booking, not the way the screen was reached. */
class TicketStateTest {
    @Test
    fun onlyAConfirmedBookingReadsAsBooked() {
        assertEquals(TicketState.Confirmed, BookingFixtures.heldBooking(status = "CONFIRMED").ticketState)
        assertEquals(TicketState.Completed, BookingFixtures.heldBooking(status = "COMPLETED").ticketState)
        assertEquals(TicketState.Cancelled, BookingFixtures.heldBooking(status = "CANCELLED").ticketState)
        assertEquals(TicketState.Expired, BookingFixtures.heldBooking(status = "EXPIRED").ticketState)
        assertEquals(TicketState.Unpaid, BookingFixtures.heldBooking(status = "HELD").ticketState)
        assertEquals(TicketState.Unpaid, BookingFixtures.heldBooking(status = "PAYMENT_PENDING").ticketState)
    }

    @Test
    fun aStatusFromLaterIsNeitherBookedNorAFailure() {
        assertEquals(TicketState.Unknown, BookingFixtures.heldBooking(status = "REFUNDED").ticketState)
    }

    /** M6-B3: a called-off date reads CANCELLED with `dateCalledOff`; the ticket says the trip was called off. */
    @Test
    fun aCalledOffDateBeatsTheStatus() {
        assertEquals(TicketState.CalledOff, BookingFixtures.heldBooking(status = "CANCELLED", dateCalledOff = true).ticketState)
        assertEquals(TicketState.CalledOff, BookingFixtures.heldBooking(status = "CONFIRMED", dateCalledOff = true).ticketState)
    }

    @Test
    fun aPaidBookingSaysPaid() {
        assertEquals(TicketPayment.Paid, BookingFixtures.heldBooking(status = "CONFIRMED", method = "card").ticketPayment)
        assertEquals(TicketPayment.Paid, BookingFixtures.heldBooking(status = "COMPLETED").ticketPayment)
        // Paid, then cancelled: the money row still tells how it was paid.
        assertEquals(TicketPayment.Paid, BookingFixtures.heldBooking(status = "CANCELLED", method = "vodafone_cash").ticketPayment)
    }

    /** M5-B3: a booking the host entered is CONFIRMED from the start; `hostPaid` false means the guest still owes. */
    @Test
    fun aHostsUnpaidBookingIsNotPaid() {
        assertEquals(TicketPayment.OwedToHost, BookingFixtures.heldBooking(status = "CONFIRMED", hostPaid = false).ticketPayment)
        assertEquals(TicketPayment.Paid, BookingFixtures.heldBooking(status = "CONFIRMED", hostPaid = true).ticketPayment)
    }

    @Test
    fun anUnpaidBookingShowsItsTotal() {
        assertEquals(TicketPayment.Unpaid, BookingFixtures.heldBooking(status = "HELD").ticketPayment)
        assertEquals(TicketPayment.Unpaid, BookingFixtures.heldBooking(status = "EXPIRED").ticketPayment)
    }
}
