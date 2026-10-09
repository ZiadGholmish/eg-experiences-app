package eg.bahr.feature.trips.model

import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes for `/api/v1/trips`, field for field with `../docs/api/openapi.yaml`.
 *
 * The list side ([TripPageDto], [TripCardDto] and its parts) follows the contract. The detail side
 * ([TripDetailDto], [DepartureDto] and below) is still the Java-era shape and moves to the
 * contract's `TripDetail` / `Departure` with the rest of M1-M1.
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

@Serializable
internal data class TripDetailDto(
    val id: Long,
    val slug: String,
    val category: String? = null,
    val title: String,
    val kicker: String? = null,
    val deck: String? = null,
    val description: String? = null,
    val pricePerPerson: MoneyDto,
    val capacity: Int = 0,
    val durationMinutes: Int? = null,
    val departurePoint: String? = null,
    val meetingPoint: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val host: HostDto? = null,
    val knowledge: HostKnowledgeDto? = null,
    val photoUrls: List<String> = emptyList(),
    val itinerary: List<ItineraryEntryDto> = emptyList(),
    val inclusions: List<InclusionDto> = emptyList(),
)

@Serializable
internal data class HostDto(
    val id: Long,
    val displayName: String,
    val verified: Boolean = false,
    val bio: String? = null,
)

/** The four host-knowledge fields the requirements doc asks every host for. */
@Serializable
internal data class HostKnowledgeDto(
    val whatToBring: String? = null,
    val bestTimeOfYear: String? = null,
    val whatToLookOutFor: String? = null,
    val whatToKnow: String? = null,
)

/** `included = false` renders as a struck-through "not included" row. */
@Serializable
internal data class InclusionDto(
    val included: Boolean,
    val text: String,
)

@Serializable
internal data class ItineraryEntryDto(
    val time: String? = null,
    val icon: String? = null,
    val text: String,
)

/**
 * A dated instance of a trip — and the seat inventory of record.
 *
 * [seatsRemaining] is a display hint only. It is read at list time and can be
 * stale by the time the user taps; the authoritative check is the conditional
 * update the backend runs when placing a hold, which is why a hold can still
 * fail with `NO_SEATS_AVAILABLE` against a departure that looked bookable.
 */
@Serializable
internal data class DepartureDto(
    val id: Long,
    val date: String,
    val departTime: String? = null,
    val returnTime: String? = null,
    val seatsRemaining: Int = 0,
    val soldOut: Boolean = false,
    val bookable: Boolean = false,
    val status: String? = null,
)
