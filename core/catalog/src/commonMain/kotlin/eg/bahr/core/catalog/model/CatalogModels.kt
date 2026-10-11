package eg.bahr.core.catalog.model

import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes the listing screens share (M4-M1c), field for field with `../docs/api/openapi.yaml`:
 * `GET /api/v1/trips`' [TripPageDto] and its [TripCardDto], the chips' [FacetDto], and the
 * `NextDeparture` / `HomeCategory` shapes Home, the trip lists and the map all read. Public because
 * three features use them (Kotlin's `internal` stops at the module).
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
data class TripPageDto(
    val items: List<TripCardDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val facets: List<FacetDto> = emptyList(),
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
data class FacetDto(
    val type: String? = null,
    val key: String? = null,
    val label: String? = null,
    val icon: String? = null,
    val tone: String? = null,
    val count: Int? = null,
    val selected: Boolean = false,
)

/** openapi `Facet.type` values. */
object FacetType {
    const val FILTER = "filter"
    const val CATEGORY = "category"
}

/** The `filter` value that means "no filter" (openapi `listTrips`: clear = omit or `all`). */
const val FILTER_ALL = "all"

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
data class TripCardDto(
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
data class ImageDto(
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
data class ImageVariantDto(
    val url: String,
    val width: Int,
    val format: String,
)

/**
 * openapi `NextDeparture`, one schema for `TripCard`, `TripDetail` and `MapPin` (M4-B3b): the trip's
 * next open date. [seatsRemaining] is a display hint, stale the moment it is read; the seat hold is
 * the authoritative check. [returnDate] is [date] plus the trip's nights (M4-B0b). [soldOut] only when
 * every upcoming open date is full.
 *
 * [id] is the departure (since M4-B3, absent from older servers); the map reads it. Last, so the
 * positional constructor calls written before it joined (M4-M1c, from the map's copy) still mean the
 * same.
 */
@Serializable
data class NextDepartureDto(
    val date: LocalDate? = null,
    val returnDate: LocalDate? = null,
    val seatsRemaining: Int? = null,
    val capacity: Int? = null,
    val soldOut: Boolean? = null,
    val id: String? = null,
)

/**
 * openapi `Badge`. [tone] is the contract's lowercase `Tone` enum, kept as a string so a tone this
 * build does not know falls back to a default instead of failing the whole page's decode.
 */
@Serializable
data class BadgeDto(
    val label: String? = null,
    val tone: String? = null,
)

@Serializable
data class RatingDto(
    val value: Double? = null,
    val count: Int? = null,
)

@Serializable
data class LocationDto(
    val lat: Double? = null,
    val lng: Double? = null,
    val label: String? = null,
)

/**
 * openapi `HomeCategory`: a Home category tile, and the map's legend entry (the colour of the pins in
 * [key]). [icon] is a Material Symbols name; [tone] the contract's `Tone`.
 */
@Serializable
data class HomeCategoryDto(
    val key: String,
    val label: String,
    val icon: String? = null,
    val tone: String,
)

/** True for a trip that stays away overnight: its duration label is words, not a time range. */
internal val TripCardDto.isMultiDay: Boolean get() = nights > 0
