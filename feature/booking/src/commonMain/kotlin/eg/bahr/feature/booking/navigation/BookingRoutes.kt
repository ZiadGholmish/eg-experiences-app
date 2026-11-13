package eg.bahr.feature.booking.navigation

import kotlinx.serialization.Serializable

/**
 * Date + party for one trip. [departureId] is the date picked on the trip page (a UUID string, as
 * the contract has it); it starts selected if it can still be booked.
 */
@Serializable
data class BookingRoute(
    val slug: String,
    val departureId: String,
)

/**
 * The held seats, handed over from date + party: everything the next screen needs without reading
 * the booking again.
 *
 * [holdExpiresAt] and [serverNow] are the server's ISO-8601 instants from `HeldSeats`; the countdown
 * (M2-M2) runs against the deadline corrected by the skew between [serverNow] and the device clock.
 * [total] is the server's total, as an amount and currency, because the client never computes one.
 * [guestPhone] is the guest's proof of ownership: without an account, `GET /bookings/{ref}` and
 * `DELETE /bookings/{ref}/hold` need it.
 *
 * [holdExpiresAt] + [serverNow] must come from the latest server answer: the screen's first deadline
 * is built from them before its own re-read lands, and stays if that re-read fails. A hold reopened
 * later (Home's "Continue your booking" card, or handed back instead of a second hold) passes a
 * fresh pair, and the hold's full length, the progress bar's 100 %, in [holdLengthSeconds], since a
 * fresh pair no longer measures it. Null when the pair is the placement's own (the bar then measures
 * `holdExpiresAt − serverNow`).
 *
 * [paymentMethods] is the server's `HeldSeats.paymentMethods` / `Booking.paymentMethods` (MV-B2), as wire strings, so
 * test cash shows from the first frame where the server takes it; the screen's own read of a HELD booking refreshes it.
 * Null when the server sent none (an older server: card and Vodafone Cash).
 */
@Serializable
data class HoldRoute(
    val ref: String,
    val holdExpiresAt: String,
    val serverNow: String,
    val totalAmount: Long,
    val totalCurrency: String,
    val guestPhone: String,
    val holdLengthSeconds: Long? = null,
    val paymentMethods: List<String>? = null,
)

/**
 * The held seats by reference only (M3-M7): a tap on the "your hold is about to expire" reminder. Read again before the
 * held seats open, since the reminder carries neither the guest's phone nor a fresh deadline.
 */
@Serializable
data class OpenHoldRoute(
    val ref: String,
)

/**
 * The booking's ticket keys on the booking reference — the thing the user is told to keep, and the thing a "where is
 * my booking" deep link will carry.
 *
 * [details] (MV-M6): opened as a booking to look at (a My trips row, a notification) rather than right after paying.
 * The bar then says "Booking details" and the ticket offers the trip's page instead of "Trips". [canRate]: My trips'
 * own rule for "Rate your trip" (`canReview` and no review yet), handed over because `getBooking` does not serve
 * `canReview` (it is on `listMyBookings` only).
 */
@Serializable
data class BookingConfirmedRoute(
    val ref: String,
    val details: Boolean = false,
    val canRate: Boolean = false,
)
