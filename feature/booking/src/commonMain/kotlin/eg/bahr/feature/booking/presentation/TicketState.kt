package eg.bahr.feature.booking.presentation

import eg.bahr.feature.booking.model.BookingDto

/*
 * What the confirmation screen says about a booking (MV-M5). The screen is reached after paying, but also by a
 * booking's reference from a notification, so the booking may be done, cancelled, expired or still unpaid: only a
 * CONFIRMED one says "Booked" and celebrates (MV-M4 review carried this).
 */

/** The booking as the ticket's header tells it. */
internal enum class TicketState {
    /** Paid, the trip ahead: the celebration, "Booked — reference", "show this screen at the bus". */
    Confirmed,

    /** Paid and the trip has run. */
    Completed,

    /** The date itself was called off (`dateCalledOff`), whatever the booking's own status. */
    CalledOff,

    /** Released by the traveller, or cancelled later. */
    Cancelled,

    /** The deadline passed before it was paid. */
    Expired,

    /** Seats held or a payment under way: not a ticket yet. */
    Unpaid,

    /** A status added to the contract after this app. */
    Unknown,
}

internal val BookingDto.ticketState: TicketState
    get() =
        when {
            // The server's word for "no bus that day" beats the status it puts next to it (M6-B3).
            dateCalledOff == true -> TicketState.CalledOff
            else ->
                when (status) {
                    STATUS_CONFIRMED -> TicketState.Confirmed
                    STATUS_COMPLETED -> TicketState.Completed
                    STATUS_CANCELLED -> TicketState.Cancelled
                    STATUS_EXPIRED -> TicketState.Expired
                    in HoldViewModel.HELD_STATUSES -> TicketState.Unpaid
                    else -> TicketState.Unknown
                }
        }

/** How the ticket's money row reads. The amount is always the server's [BookingDto.total]: the app adds nothing up. */
internal enum class TicketPayment {
    /** Paid in the app, or to the host (`hostPaid` true): "Paid". */
    Paid,

    /** A booking the host entered that is not paid yet (`hostPaid` false): "To pay the host". */
    OwedToHost,

    /** Not paid (held, expired, cancelled before paying, unknown): "Total". */
    Unpaid,
}

internal val BookingDto.ticketPayment: TicketPayment
    get() =
        when {
            // Only a host's own booking carries hostPaid; CONFIRMED alone does not mean paid there (M5-B3).
            hostPaid == false -> TicketPayment.OwedToHost
            status in PAID_STATUSES || hostPaid == true || method != null -> TicketPayment.Paid
            else -> TicketPayment.Unpaid
        }

// openapi `Booking.status` values this file names (HELD and PAYMENT_PENDING are HoldViewModel's).
private const val STATUS_CONFIRMED = "CONFIRMED"
private const val STATUS_COMPLETED = "COMPLETED"
private const val STATUS_CANCELLED = "CANCELLED"
private const val STATUS_EXPIRED = "EXPIRED"
