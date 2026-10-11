package eg.bahr.feature.trips.model

import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.LocationDto
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.catalog.model.RatingDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes for `/api/v1/trips`, field for field with `../docs/api/openapi.yaml`.
 *
 * A "See all" page is [TripCardPageDto]; the trip page is [TripDetailDto] (`TripDetail`) and
 * [DepartureDto] (`Departure`, also `GET /trips/{slug}/departures`). Joining a sold-out date's
 * waiting list sends a [WaitlistRequest]. The list's own shapes (`TripPage`, `TripCard`, `Facet`, …)
 * are core:catalog's, shared with Home and the map (M4-M1c).
 *
 * The backend omits null fields, so everything the contract does not mark `required` is nullable
 * with a default here. When the backend adds a field, add it the same way: the JSON parser ignores
 * unknown keys, so an old app keeps working, and a non-nullable addition would break it instead.
 */

/**
 * openapi `TripCardPage` (`listHomeSectionTrips`): a page of a Home row's whole list, with no chips.
 */
@Serializable
internal data class TripCardPageDto(
    val items: List<TripCardDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
)

/**
 * openapi `TripDetail`: the [TripCardDto] fields (the contract's `allOf` merges them flat) plus the
 * trip page. [shareUrl] is served since M1-B5 and not used yet (no share button); [dates] are the same `Departure`
 * shape as the departures endpoint. [returnLabel] (M4-B0b) is the facts grid's "Back Fri 22:00" note on a
 * multi-day trip; absent on a day trip.
 */
@Serializable
internal data class TripDetailDto(
    val slug: String,
    val title: String,
    val subtitle: String? = null,
    val durationLabel: String,
    val durationMinutes: Int? = null,
    val nights: Int = 0,
    val price: MoneyDto,
    val categories: List<String> = emptyList(),
    val heroImage: ImageDto? = null,
    val cardImage: ImageDto? = null,
    val nextDeparture: NextDepartureDto? = null,
    val badge: BadgeDto? = null,
    val rating: RatingDto? = null,
    val location: LocationDto? = null,
    val deck: String? = null,
    val returnLabel: String? = null,
    val departure: DeparturePointDto? = null,
    val destination: DestinationDto? = null,
    val included: List<String> = emptyList(),
    val excluded: List<String> = emptyList(),
    val itinerary: List<ItineraryStopDto> = emptyList(),
    val host: HostDto? = null,
    val tips: List<TipDto> = emptyList(),
    val reviews: ReviewsDto? = null,
    val dates: List<DepartureDto> = emptyList(),
    val policy: PolicyDto? = null,
    val gallery: List<ImageDto> = emptyList(),
    val og: OpenGraphDto? = null,
    val shareUrl: String? = null,
)

/** openapi `Place` + `timeLocal`/`arriveBy` ("HH:mm", Cairo): the facts grid's "Bus leaves" cell. */
@Serializable
internal data class DeparturePointDto(
    val placeName: String? = null,
    val city: String? = null,
    val governorate: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val timeLocal: String? = null,
    val arriveBy: String? = null,
)

/** openapi `Place` + `distanceKm`/`travelTime` ("2h40"): the facts grid's "Trip is at" cell. */
@Serializable
internal data class DestinationDto(
    val placeName: String? = null,
    val city: String? = null,
    val governorate: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val distanceKm: Int? = null,
    val travelTime: String? = null,
)

/**
 * One stop of the day. [time] is "HH:mm"; [approximate] means the client prints it as approximate.
 * [icon] is a Material Symbols name and [kind] the contract's lowercase `transit`/`meal`/`activity`,
 * both kept as strings so a new value falls back instead of failing the page.
 */
@Serializable
internal data class ItineraryStopDto(
    val time: String? = null,
    val text: String? = null,
    val icon: String? = null,
    val kind: String? = null,
    val approximate: Boolean = false,
)

/**
 * The host card. The contract has no id or display-name field: [name] is what is shown. [avatar] may
 * be absent (a host without one), so the card falls back to the initial. The public page carries no
 * phone number: that is only on a paid booking (product decision D4).
 */
@Serializable
internal data class HostDto(
    val name: String? = null,
    val avatar: ImageDto? = null,
    val role: String? = null,
    val tripsRun: Int? = null,
    val verified: Boolean = false,
)

/** A host tip. [key] is the contract's lowercase `bring`/`best_time`/`look_out`/`know`. */
@Serializable
internal data class TipDto(
    val key: String? = null,
    val title: String? = null,
    val body: String? = null,
)

/** The authored rating summary and the curated reviews under it. */
@Serializable
internal data class ReviewsDto(
    val average: Double? = null,
    val count: Int? = null,
    val items: List<ReviewDto> = emptyList(),
)

/** One review. [tone] tints the author's initial (the contract's `Tone`). */
@Serializable
internal data class ReviewDto(
    val author: String? = null,
    val dateISO: LocalDate? = null,
    val body: String? = null,
    val partyLabel: String? = null,
    val tone: String? = null,
)

/**
 * The rules the booking engine applies, stated on the page (from the backend's policy config). Every
 * seat is charged the trip's price whatever the passenger's age (product decision D1), so there is no
 * child rule to state.
 */
@Serializable
internal data class PolicyDto(
    val freeCancellationHours: Int? = null,
    val maxPartySize: Int? = null,
)

/** Link-preview fields; the app does not render them. */
@Serializable
internal data class OpenGraphDto(
    val image: ImageDto? = null,
    val title: String? = null,
    val description: String? = null,
)

/**
 * openapi `Departure`: one date and the seat inventory of record. [id] is a UUID string. [departTime]
 * and [returnTime] are "HH:mm" Cairo time; [returnTime] falls on [returnDate], which is [date] on a
 * day trip and later on a multi-day one (M4-B0b; absent from older servers). [dayLabel] is the server's localised "Sat 10 Oct"; the
 * date card formats [date] itself because it shows the day and the date on two lines.
 *
 * [seatsRemaining] is a display hint only. It is read at list time and can be stale by the time
 * the user taps; the authoritative check is the conditional update the backend runs when placing a
 * hold, which is why a hold can still fail with `NO_SEATS_AVAILABLE` against a date that looked
 * bookable. [bookable] is false when sold out, cancelled or past the cutoff.
 *
 * [unavailableReason] says why a date cannot be booked (`SOLD_OUT`, `CANCELLED`, `CLOSED`) and is
 * absent when it can. It is a string, not an enum, because the contract may add a reason and an
 * unknown enum value would fail the whole page's decode; see `DateAvailability` for how it is read.
 */
@Serializable
internal data class DepartureDto(
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

/**
 * openapi `WaitlistRequest` (`POST /departures/{departureId}/waitlist`, `joinWaitlist`): a phone and a
 * party size for a sold-out date (D3: only `SOLD_OUT` has a list).
 *
 * [locale] is left out (null is not sent): the contract defaults it to the request's
 * `Accept-Language`, which is already the app's stored language.
 */
@Serializable
internal data class WaitlistRequest(
    val phone: String,
    val partySize: Int,
    val locale: String? = null,
)

/** True for a trip that stays away overnight: its duration label is words, not a time range. */
internal val TripDetailDto.isMultiDay: Boolean get() = nights > 0

/** The day this date is back, when that is a later day than it leaves (a multi-day trip); else null. */
internal val DepartureDto.laterReturnDate: LocalDate? get() = returnDate?.takeIf { it > date }
