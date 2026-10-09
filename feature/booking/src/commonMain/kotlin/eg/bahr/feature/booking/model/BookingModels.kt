package eg.bahr.feature.booking.model

import eg.bahr.core.network.MoneyDto
import kotlinx.serialization.Serializable

/**
 * Wire shapes for `/api/v1/bookings`.
 *
 * Target: the booking schemas in `../docs/api/openapi.yaml` (`PlaceHoldRequest`,
 * `HeldSeats`, `Booking`). These are still the Java-era shapes until M2 realigns
 * them: ids here are `Long` where the contract uses uuid strings, the hold lacks
 * `serverNow` (needed for the deadline-driven countdown) and `fee`, and the
 * contract's `Booking` nests `trip{}`, `departure{}` and `host{}` where this is flat.
 */
@Serializable
internal data class PlaceHoldRequest(
    val departureId: Long,
    val partySize: Int,
    val guest: GuestRequest,
)

/**
 * The lead contact. A booking can be made without an account — the whole point
 * of release 1 is that someone opens a shared link and books cold — so the name
 * and phone travel with the request rather than coming from a session.
 */
@Serializable
internal data class GuestRequest(
    val name: String,
    val phone: String,
    val locale: String,
)

/**
 * Seats held, not yet paid for.
 *
 * [holdExpiresAt] is the server's clock, not the device's. The countdown the
 * checkout screen shows is computed against it, and when it runs out the
 * client must re-read rather than assume the hold survived.
 */
@Serializable
internal data class HeldSeatsDto(
    val ref: String,
    val departureId: Long,
    val partySize: Int,
    val pricePerPerson: MoneyDto,
    val total: MoneyDto,
    val holdExpiresAt: String? = null,
    val seatsRemaining: Int = 0,
)

/** The ticket. Everything needed to show up in the right place at the right time. */
@Serializable
internal data class BookingDto(
    val ref: String,
    val status: String,
    val departureId: Long,
    val tripTitle: String? = null,
    val departurePoint: String? = null,
    val meetingPoint: String? = null,
    val departureDate: String? = null,
    val departTime: String? = null,
    val partySize: Int = 0,
    val guestName: String? = null,
    val pricePerPerson: MoneyDto? = null,
    val total: MoneyDto? = null,
    val holdExpiresAt: String? = null,
    val confirmedAt: String? = null,
)
