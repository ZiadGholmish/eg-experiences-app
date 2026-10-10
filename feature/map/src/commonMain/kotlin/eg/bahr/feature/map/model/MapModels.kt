package eg.bahr.feature.map.model

import eg.bahr.core.network.MoneyDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes for `GET /api/v1/trips/map` (`getTripMap`), field for field with
 * `../docs/api/openapi.yaml`: `MapData`, `MapPin`, `MapPlace`, `HomeCategory`, `NextDeparture`.
 *
 * This feature keeps its own copies of `NextDeparture` and `HomeCategory` (feature:trips has them
 * too): a feature never imports another, and neither shape carries screen logic worth promoting to
 * `core:*` yet. `Money` is core:network's [MoneyDto].
 *
 * Enum-like values (`tone`) stay strings, so a value added later draws as the default colour instead
 * of failing the whole map. Optional fields are nullable with a default, so an older server (no
 * `departTime` before M4-B3b, no `nextDeparture.id` before M4-B3) still decodes.
 */

/** openapi `MapData`. */
@Serializable
internal data class TripMapDto(
    val pins: List<MapPinDto> = emptyList(),
    val departurePoints: List<MapPlaceDto> = emptyList(),
    val legend: List<MapLegendCategoryDto> = emptyList(),
)

/**
 * openapi `MapPin`: one published trip at its destination. [departTime] is the bus's `HH:mm` (Cairo
 * wall clock) from [departurePointId]; [distanceKm] the stored road distance between the two.
 */
@Serializable
internal data class MapPinDto(
    val slug: String,
    val title: String,
    val subtitle: String? = null,
    val price: MoneyDto,
    val durationLabel: String,
    val nights: Int = 0,
    val lat: Double,
    val lng: Double,
    val placeName: String? = null,
    val category: String? = null,
    val tone: String,
    val nextDeparture: MapNextDepartureDto? = null,
    val departTime: String? = null,
    val departurePointId: String? = null,
    val distanceKm: Int? = null,
)

/** openapi `NextDeparture`, the trip's `TripCard.nextDeparture`. [soldOut] only when every open date is full. */
@Serializable
internal data class MapNextDepartureDto(
    val id: String? = null,
    val date: LocalDate? = null,
    val returnDate: LocalDate? = null,
    val seatsRemaining: Int? = null,
    val capacity: Int? = null,
    val soldOut: Boolean? = null,
)

/** openapi `MapPlace`: a place a bus leaves from. It carries no time; the pins leaving from it do. */
@Serializable
internal data class MapPlaceDto(
    val id: String,
    val placeName: String,
    val city: String? = null,
    val governorate: String? = null,
    val lat: Double,
    val lng: Double,
)

/** openapi `HomeCategory`, as the map's legend: [tone] is the colour of the pins in [key]. */
@Serializable
internal data class MapLegendCategoryDto(
    val key: String,
    val label: String,
    val icon: String? = null,
    val tone: String,
)
