package eg.bahr.feature.trips.model

import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes for `/api/v1/trips`, field for field with `../docs/api/openapi.yaml`.
 *
 * The list side is [TripPageDto] and [TripCardDto]; the trip page is [TripDetailDto] (`TripDetail`)
 * and [DepartureDto] (`Departure`, also `GET /trips/{slug}/departures`).
 *
 * The backend omits null fields, so everything the contract does not mark `required` is nullable
 * with a default here. When the backend adds a field, add it the same way: the JSON parser ignores
 * unknown keys, so an old app keeps working, and a non-nullable addition would break it instead.
 */

/** openapi `TripPage`. [facets] is declared by the contract but not served before M4. */
@Serializable
internal data class TripPageDto(
    val items: List<TripCardDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val facets: List<FacetDto> = emptyList(),
) {
    val hasMore: Boolean get() = page + 1 < totalPages
}

/** openapi `Facet`: one filter chip with its live count. */
@Serializable
internal data class FacetDto(
    val key: String? = null,
    val label: String? = null,
    val icon: String? = null,
    val count: Int? = null,
)

/**
 * openapi `TripCard`: one trip in a list. Every text arrives already in the request's
 * `Accept-Language`, so nothing here is translated on the client.
 *
 * [categories] are category *keys* (`on_the_boat`), not labels; the labels come with Home (M4).
 * [durationLabel] is `05:00 → 22:00`, or a bare duration (`17h`) when the times are unknown, and
 * may be empty.
 */
@Serializable
internal data class TripCardDto(
    val slug: String,
    val title: String,
    val subtitle: String? = null,
    val durationLabel: String,
    val durationMinutes: Int? = null,
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
 * the seat hold is the authoritative check.
 */
@Serializable
internal data class NextDepartureDto(
    val date: LocalDate? = null,
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
 * shape as the departures endpoint.
 */
@Serializable
internal data class TripDetailDto(
    val slug: String,
    val title: String,
    val subtitle: String? = null,
    val durationLabel: String,
    val durationMinutes: Int? = null,
    val price: MoneyDto,
    val categories: List<String> = emptyList(),
    val heroImage: ImageDto? = null,
    val cardImage: ImageDto? = null,
    val nextDeparture: NextDepartureDto? = null,
    val badge: BadgeDto? = null,
    val rating: RatingDto? = null,
    val location: LocationDto? = null,
    val deck: String? = null,
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
 * be absent (a host without one), so the card falls back to the initial. [phone] is decoded but not shown on the trip page (open product question D4).
 */
@Serializable
internal data class HostDto(
    val name: String? = null,
    val avatar: ImageDto? = null,
    val role: String? = null,
    val tripsRun: Int? = null,
    val verified: Boolean = false,
    val phone: String? = null,
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

/** The rules the booking engine applies, stated on the page (from the backend's policy config). */
@Serializable
internal data class PolicyDto(
    val freeCancellationHours: Int? = null,
    val childFreeUnder: Int? = null,
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
 * and [returnTime] are "HH:mm" Cairo time. [dayLabel] is the server's localised "Sat 10 Oct"; the
 * date card formats [date] itself because it shows the day and the date on two lines.
 *
 * [seatsRemaining] is a display hint only. It is read at list time and can be stale by the time
 * the user taps; the authoritative check is the conditional update the backend runs when placing a
 * hold, which is why a hold can still fail with `NO_SEATS_AVAILABLE` against a date that looked
 * bookable. [bookable] is false when sold out, cancelled or past the cutoff.
 */
@Serializable
internal data class DepartureDto(
    val id: String,
    val date: LocalDate,
    val dayLabel: String? = null,
    val departTime: String? = null,
    val returnTime: String? = null,
    val seatsRemaining: Int,
    val capacity: Int,
    val soldOut: Boolean,
    val bookable: Boolean,
    val price: MoneyDto,
)
