package eg.bahr.feature.booking.model

import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes for the date + party screen, field for field with `../docs/api/openapi.yaml`.
 *
 * This feature reads the trip and its dates itself rather than taking them from `feature:trip-details`:
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
    /**
     * Since MV-B2: the methods `startPayment` takes now (`card`, `vodafone_cash`, `test_cash` where the server takes
     * it). Strings, not an enum, so a method added later cannot fail the decode; unknown ones are dropped when read
     * (`PaymentMethod.offered`). Absent (an older server): card and Vodafone Cash.
     */
    val paymentMethods: List<String>? = null,
)

/**
 * openapi `Booking` (`GET /bookings/{ref}`), read by the held-seats screen to re-sync its countdown
 * and to show the trip summary, by Home's "Continue your booking" card, and since MV-M4 by the
 * confirmation screen (which until then decoded a Java-era flat shape that the contract's `Booking`
 * never matched, so a paid booking ended on its error view: M3-M1 review #7).
 *
 * **A partial copy of the contract's `Booking`, on purpose:** only the fields these screens read.
 * Left out: `host`, `calendar`, `Place.lat`/`lng` and `Image.variants` (unknown keys are ignored on
 * decode, so the full payload still parses). Whoever needs one of them adds it here, nullable with a
 * default, field-for-field with `openapi.yaml`.
 *
 * [status] is a string, not an enum: anything other than `HELD` / `PAYMENT_PENDING` means the seats
 * are no longer held for this booking, and a status added later must not fail the decode. A released
 * hold reads `CANCELLED`; one past its deadline reads `EXPIRED` with no [holdExpiresAt].
 * `host` is left out: the confirmation screen does not show it yet (M3-M2's full ticket).
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
    /** Since MV-B2: how it was paid (`card`, `vodafone_cash`, `test_cash`); absent while unpaid. A string, as [status]. */
    val method: String? = null,
    /** Since MV-B2, only while HELD: as [HeldSeatsDto.paymentMethods], for a hold reopened or read again. */
    val paymentMethods: List<String>? = null,
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

/**
 * openapi `StartPaymentRequest` (`startPayment`, M3-B2). [method] is the contract's lower-case wire value (`card`,
 * `vodafone_cash`, and since MV-B2 `test_cash`). [payer] is required for every method, test cash included. [returnUrl] is where the hosted checkout sends the browser back to (the app's own link, which the
 * server must allow); left out, the provider's default page. [guestPhone] is the phone the hold was made under: it
 * proves a hold not yet on the signed-in account is this person's, in the body so it never reaches access logs.
 */
@Serializable
internal data class StartPaymentRequest(
    val method: String,
    val payer: PayerRequest,
    val returnUrl: String? = null,
    val guestPhone: String? = null,
)

/** openapi `StartPaymentRequest.payer`: who pays. [phone] is the Vodafone Cash number when paying with it. */
@Serializable
internal data class PayerRequest(
    val name: String,
    val phone: String,
)

/**
 * openapi `PaymentStarted`. The booking is PAYMENT_PENDING from now on; the payment's outcome arrives by webhook, so
 * the app polls `getBooking`. [holdExpiresAt] is the deadline the customer already sees, unchanged.
 */
@Serializable
internal data class PaymentStartedDto(
    val paymentId: String,
    val action: PaymentActionDto,
    val holdExpiresAt: String,
)

/**
 * openapi `PaymentStarted.action`. [type] is a string, not an enum, so an action added to the contract later cannot
 * fail the decode: `REDIRECT` opens [url] (the hosted checkout), `WALLET_PENDING` has nothing to open, `CONFIRMED`
 * (MV-B2, test cash) means the booking is already paid (nothing to open, nothing to poll), anything else is treated as
 * nothing to open.
 */
@Serializable
internal data class PaymentActionDto(
    val type: String,
    val url: String? = null,
)
