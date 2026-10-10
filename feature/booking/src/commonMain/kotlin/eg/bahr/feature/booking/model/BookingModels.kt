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
 *
 * [nights] (M4-B0b, absent = 0): on a multi-day trip [durationLabel] is words (`2 days · 1 night`),
 * not a time range, so it is not forced left to right.
 */
@Serializable
internal data class BookingTripDto(
    val slug: String,
    val title: String,
    val durationLabel: String? = null,
    val nights: Int = 0,
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
 * [returnTime] are "HH:mm" Cairo time; [returnTime] is on [returnDate], a later day on a multi-day
 * trip (M4-B0b; absent from older servers). [seatsRemaining] is a display hint only: the hold is the
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
    val returnDate: LocalDate? = null,
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

/**
 * openapi `Booking` (`GET /bookings/{ref}`), read by the held-seats screen to re-sync its countdown
 * and to show the trip summary, and by Home's "Continue your booking" card. Separate from
 * [BookingDto], the Java-era shape the M3 confirmation screen still uses, so that rewrite stays M3's.
 *
 * **A partial copy of the contract's `Booking`, on purpose:** only the fields these screens read.
 * Left out: `host`, `calendar`, `Place.lat`/`lng` and `Image.variants` (unknown keys are ignored on
 * decode, so the full payload still parses). Whoever needs one of them adds it here, nullable with a
 * default, field-for-field with `openapi.yaml`.
 *
 * [status] is a string, not an enum: anything other than `HELD` / `PAYMENT_PENDING` means the seats
 * are no longer held for this booking, and a status added later must not fail the decode. A released
 * hold reads `CANCELLED`; one past its deadline reads `EXPIRED` with no [holdExpiresAt].
 * `host` is left out: it is only served on a paid booking, which this screen never shows.
 */
@Serializable
internal data class HeldBookingDto(
    val ref: String,
    val status: String,
    val holdExpiresAt: String? = null,
    val serverNow: String,
    val trip: BookingTripSummaryDto? = null,
    val date: LocalDate,
    val dayLabel: String? = null,
    val departure: BookingDeparturePlaceDto? = null,
    val returnTime: String? = null,
    val returnDate: LocalDate? = null,
    val partySize: Int,
    val total: MoneyDto,
    val paid: MoneyDto? = null,
    val method: String? = null,
)

/** openapi `Booking.trip`. */
@Serializable
internal data class BookingTripSummaryDto(
    val slug: String? = null,
    val title: String? = null,
    val cardImage: BookingImageDto? = null,
)

/** openapi `Image`, the fields this screen draws: the photo and its LQIP placeholder. */
@Serializable
internal data class BookingImageDto(
    val url: String,
    val width: Int,
    val height: Int,
    val alt: String? = null,
    val lqip: String? = null,
)

/** openapi `Booking.departure`: a `Place` plus the local departure time. */
@Serializable
internal data class BookingDeparturePlaceDto(
    val placeName: String? = null,
    val city: String? = null,
    val governorate: String? = null,
    val timeLocal: String? = null,
    val arriveBy: String? = null,
    val whereToStand: String? = null,
)

/** The day this date is back when that is later than the day it leaves (a multi-day trip), else null. */
internal val BookingDepartureDto.laterReturnDate: LocalDate? get() = returnDate?.takeIf { it > date }

/**
 * The same for a booking. `Booking` carries no `nights`, so a multi-day booking is one whose
 * [HeldBookingDto.returnDate] is after its [HeldBookingDto.date].
 */
internal val HeldBookingDto.laterReturnDate: LocalDate? get() = returnDate?.takeIf { it > date }
