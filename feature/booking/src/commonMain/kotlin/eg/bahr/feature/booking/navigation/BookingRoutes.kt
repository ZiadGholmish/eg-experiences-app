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
)

/**
 * The confirmation screen keys on the booking reference — the thing the user is
 * told to keep, and the thing a "where is my booking" deep link will carry.
 */
@Serializable
data class BookingConfirmedRoute(
    val ref: String,
)
