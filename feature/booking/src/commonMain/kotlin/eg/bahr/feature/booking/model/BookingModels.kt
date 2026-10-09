package eg.bahr.feature.booking.model

import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes for the date + party screen, field for field with `../docs/api/openapi.yaml`.
 *
 * This feature reads the trip and its dates itself rather than taking them from `feature:trips`:
 * features do not depend on each other, and only the slug and the picked departure id travel through
 * navigation (bahr-modularization: "pass ids, let each feature fetch its own").
 *
 * Everything the contract does not mark `required` is nullable with a default, as the backend omits
 * nulls; the JSON parser ignores unknown keys, so an old app keeps working when a field is added.
 */

/**
 * openapi `TripDetail`, only the fields this screen shows: the heading, the per-person price and the
 * party limit. The other fields of the shape are ignored on decode.
 */
@Serializable
internal data class BookingTripDto(
    val slug: String,
    val title: String,
    val durationLabel: String? = null,
    val price: MoneyDto,
    val dates: List<BookingDepartureDto> = emptyList(),
    val policy: BookingPolicyDto? = null,
)

/** openapi `TripDetail.policy`. [maxPartySize] is the stepper's upper bound (`policy.maxPartySize`). */
@Serializable
internal data class BookingPolicyDto(
    val freeCancellationHours: Int? = null,
    val maxPartySize: Int? = null,
)

/**
 * openapi `Departure` (`GET /trips/{slug}/departures`). [id] is a UUID string. [departTime] and
 * [returnTime] are "HH:mm" Cairo time. [seatsRemaining] is a display hint only: the hold is the
 * authoritative check and can still answer `NO_SEATS_AVAILABLE`.
 *
 * [unavailableReason] (`SOLD_OUT`, `CANCELLED`, `CLOSED`, absent when bookable) is a string, not an
 * enum, so a reason added to the contract later cannot fail the decode; see `DateAvailability`.
 */
@Serializable
internal data class BookingDepartureDto(
    val id: String,
    val date: LocalDate,
    val dayLabel: String? = null,
    val departTime: String? = null,
    val returnTime: String? = null,
    val seatsRemaining: Int,
    val capacity: Int,
    val soldOut: Boolean,
    val bookable: Boolean,
    val unavailableReason: String? = null,
    val price: MoneyDto,
)

/** openapi `PlaceHoldRequest`. */
@Serializable
internal data class PlaceHoldRequest(
    val departureId: String,
    val partySize: Int,
    val guest: GuestRequest,
)

/**
 * The lead contact. A booking can be made without an account (someone opens a shared link and books
 * cold), so the name and phone travel with the request rather than coming from a session.
 *
 * [locale] is left out: the contract defaults it to the request's `Accept-Language`, which is already
 * the app's stored language.
 */
@Serializable
internal data class GuestRequest(
    val name: String,
    val phone: String,
    val locale: String? = null,
)

/**
 * openapi `HeldSeats`: seats held, not yet paid for.
 *
 * [holdExpiresAt] is the server's deadline and [serverNow] the server's clock when it answered; the
 * countdown (M2-M2) runs against the first, corrected by the skew between the second and the device.
 * [total] is the server's sum: the client never multiplies a price by a party size. [fee] is not
 * served in R1.
 */
@Serializable
internal data class HeldSeatsDto(
    val ref: String,
    val departureId: String? = null,
    val partySize: Int? = null,
    val pricePerPerson: MoneyDto? = null,
    val fee: MoneyDto? = null,
    val total: MoneyDto,
    val holdExpiresAt: String,
    val serverNow: String,
    val seatsRemaining: Int? = null,
)

/**
 * The confirmation screen's booking. Still the Java-era flat shape (`departureId: Long`, no
 * `serverNow`, no nested `trip`/`departure`/`host`), which does not decode the contract's `Booking`;
 * the confirmation screen that reads it is unreachable until M3 rewrites it on the contract.
 */
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
