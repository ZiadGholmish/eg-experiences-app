package eg.bahr.feature.trips.model

import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes for `/api/v1/trips`, field for field with `../docs/api/openapi.yaml`.
 *
 * The list side is [TripPageDto] and [TripCardDto]; the trip page is [TripDetailDto] (`TripDetail`)
 * and [DepartureDto] (`Departure`, also `GET /trips/{slug}/departures`). Joining a sold-out date's
 * waiting list sends a [WaitlistRequest].
 *
 * The backend omits null fields, so everything the contract does not mark `required` is nullable
 * with a default here. When the backend adds a field, add it the same way: the JSON parser ignores
 * unknown keys, so an old app keeps working, and a non-nullable addition would break it instead.
 */

/**
 * openapi `TripPage` (`listTrips`). [facets] are the chips above a filtered list (M4-B1): every chip,
 * zero counts included, in the served order (filters first, then categories).
 */
@Serializable
internal data class TripPageDto(
    val items: List<TripCardDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val facets: List<FacetDto> = emptyList(),
)

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
 * openapi `Facet`: one chip with its live count. [type] says which query parameter the chip sets
 * (`filter` or `category`, see [FacetType]); a chip is identified by [type] and [key] together, because
 * a category may share a key with a filter value. [tone] is on category chips only. A [count] of 0 is
 * a dimmed chip, never a hidden one. [selected] marks the request's active chip of each type.
 *
 * Every field has a default although the contract marks most required: the backend omits nulls, and
 * one odd chip must not fail the page the trips are on.
 */
@Serializable
internal data class FacetDto(
    val type: String? = null,
    val key: String? = null,
    val label: String? = null,
    val icon: String? = null,
    val tone: String? = null,
    val count: Int? = null,
    val selected: Boolean = false,
)

/** openapi `Facet.type` values. */
internal object FacetType {
    const val FILTER = "filter"
    const val CATEGORY = "category"
}

/** The `filter` value that means "no filter" (openapi `listTrips`: clear = omit or `all`). */
internal const val FILTER_ALL = "all"

/**
 * openapi `TripCard`: one trip in a list. Every text arrives already in the request's
 * `Accept-Language`, so nothing here is translated on the client.
 *
 * [categories] are category *keys* (`on_the_boat`), not labels; the labels come with Home (M4).
 * [durationLabel] is `05:00 → 22:00`, or a bare duration (`17h`) when the times are unknown, and
 * may be empty. On a multi-day trip ([nights] > 0, M4-B0b) it is words (`2 days · 1 night` /
 * `يومان · ليلة واحدة`), so it must not be forced left to right; see [isMultiDay].
 *
 * [nights] is non-null with the contract's default 0 rather than nullable: the contract says to treat
 * an absent value as 0 (a day trip), so an older server's payload still decodes to the right thing.
 */
@Serializable
internal data class TripCardDto(
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
)

/**
 * openapi `Image`. [width] and [height] are there so the photo's space is laid out before it loads;
 * [lqip] is a tiny blurred `data:` URI shown while the real photo downloads.
 */
@Serializable
internal data class ImageDto(
    val url: String,
    val width: Int,
    val height: Int,
    val alt: String? = null,
    val lqip: String? = null,
    val variants: List<ImageVariantDto> = emptyList(),
)

/**
 * openapi `ImageVariant`: a smaller re-encoded copy. [format] is the contract's lowercase
 * `avif`/`webp`/`jpeg`, kept as a string so a new format cannot fail the decode. Decoded but not
 * picked from yet: the app loads [ImageDto.url].
 */
@Serializable
internal data class ImageVariantDto(
    val url: String,
    val width: Int,
    val format: String,
)

/**
 * The trip's next open date. [seatsRemaining] is a display hint, stale the moment it is read;
 * the seat hold is the authoritative check. [returnDate] is [date] plus the trip's nights (M4-B0b).
 */
@Serializable
internal data class NextDepartureDto(
    val date: LocalDate? = null,
    val returnDate: LocalDate? = null,
    val seatsRemaining: Int? = null,
    val capacity: Int? = null,
    val soldOut: Boolean? = null,
)

/**
 * openapi `Badge`. [tone] is the contract's lowercase `Tone` enum, kept as a string so a tone this
 * build does not know falls back to a default instead of failing the whole page's decode.
 */
@Serializable
internal data class BadgeDto(
    val label: String? = null,
    val tone: String? = null,
)

@Serializable
internal data class RatingDto(
    val value: Double? = null,
    val count: Int? = null,
)

@Serializable
internal data class LocationDto(
    val lat: Double? = null,
    val lng: Double? = null,
    val label: String? = null,
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
internal val TripCardDto.isMultiDay: Boolean get() = nights > 0

internal val TripDetailDto.isMultiDay: Boolean get() = nights > 0

/** The day this date is back, when that is a later day than it leaves (a multi-day trip); else null. */
internal val DepartureDto.laterReturnDate: LocalDate? get() = returnDate?.takeIf { it > date }
