package eg.bahr.feature.map.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.departure_sold_out
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.core.localization.generated.resources.map_departure_label
import eg.bahr.core.localization.generated.resources.map_legend_departure
import eg.bahr.core.localization.generated.resources.trip_fact_distance
import eg.bahr.feature.map.model.MapPinDto
import eg.bahr.feature.map.presentation.DepartureMarker
import eg.bahr.feature.map.presentation.GeoPoint
import eg.bahr.feature.map.presentation.TripMapUiState
import eg.bahr.feature.map.presentation.cameraPoints
import eg.bahr.feature.map.presentation.departureMarkers
import eg.bahr.feature.map.presentation.driveLine
import org.jetbrains.compose.resources.stringResource

/**
 * The state as the map draws it: pins (in their tone, grey when sold out, ringed when selected), the
 * dark departure marker and the dashed drive with its distance tag, and where the camera goes.
 * Pictures are drawn again only when what they show changes. Null until the map has pins.
 */
@Composable
internal fun rememberTripMapScene(state: TripMapUiState): TripMapScene? {
    val data = state.map ?: return null
    val painter = rememberMarkerPainter()
    val palette = rememberMapPalette()
    val arabic = BahrTheme.locale.isArabic
    val density = LocalDensity.current

    val prices = data.pins.map { BahrFormat.money(it.price.amount, it.price.currencyCode, arabic) }
    val pinImages =
        remember(data.pins, prices, state.selectedSlug, painter, palette) {
            data.pins.mapIndexed { i, pin ->
                val colors = if (pin.isSoldOut) palette.soldOut else palette.tone(pin.tone)
                painter.pin(prices[i], colors, selected = pin.slug == state.selectedSlug)
            }
        }
    val soldOutWord = stringResource(Res.string.departure_sold_out)
    val pins =
        data.pins.mapIndexed { i, pin ->
            val selected = pin.slug == state.selectedSlug
            val named = stringResource(Res.string.format_pair, pin.title, prices[i])
            MapMarker(
                id = pin.slug,
                point = GeoPoint(pin.lat, pin.lng),
                image = pinImages[i],
                // The open card's pin above the others; a sold-out one under the bookable ones.
                zIndex =
                    if (selected) {
                        Z_SELECTED
                    } else if (pin.isSoldOut) {
                        Z_SOLD_OUT
                    } else {
                        Z_PIN
                    },
                clickable = true,
                description = if (pin.isSoldOut) stringResource(Res.string.format_pair, named, soldOutWord) else named,
            )
        }

    val departures = state.departureMarkers()
    val departureLabels = departures.map { departureLabel(it) }
    val departureImages = remember(departureLabels, painter, palette) { departureLabels.map { painter.pin(it, palette.departure) } }
    val departureWord = stringResource(Res.string.map_legend_departure)
    val buses =
        departures.mapIndexed { i, marker ->
            MapMarker(
                id = DEPARTURE_ID_PREFIX + marker.point.id,
                point = GeoPoint(marker.point.lat, marker.point.lng),
                image = departureImages[i],
                zIndex = Z_DEPARTURE,
                clickable = false,
                description = stringResource(Res.string.format_pair, departureWord, departureLabels[i]),
            )
        }

    val line = state.driveLine()
    val distanceLabel = line?.distanceKm?.let { stringResource(Res.string.trip_fact_distance, it) }
    val distanceImage = remember(distanceLabel, painter, palette) { distanceLabel?.let { painter.tag(it, palette.distance) } }
    val distance =
        if (line != null && distanceLabel != null && distanceImage != null) {
            // On the line's middle: the line is straight (the contract says so), the distance is the road's.
            val middle = GeoPoint((line.from.lat + line.to.lat) / 2, (line.from.lng + line.to.lng) / 2)
            listOf(MapMarker(DISTANCE_ID, middle, distanceImage, Z_DISTANCE, clickable = false, description = distanceLabel))
        } else {
            emptyList()
        }

    val route =
        line?.let {
            with(density) {
                MapRoute(
                    from = GeoPoint(it.from.lat, it.from.lng),
                    to = GeoPoint(it.to.lat, it.to.lng),
                    color = palette.route,
                    widthPx = BahrSize.routeLine.toPx(),
                    dashPx = BahrSize.routeDash.toPx(),
                    gapPx = BahrSize.routeGap.toPx(),
                )
            }
        }
    val bounds = GeoBounds.around(state.cameraPoints()) ?: return null
    val markers = pins + buses + distance
    // The camera frames the points the pins stand on; a pin's picture rises above its point, so the
    // tallest one is kept inside the frame too, or a pin at the top edge is cut off.
    val tallest = markers.maxOfOrNull { it.image.bitmap.height } ?: 0
    val camera =
        MapCamera(
            id = state.camera.id,
            bounds = bounds,
            edgePaddingPx = with(density) { BahrSpacing.lg.roundToPx() } + tallest,
            // The platform maps never animate their first framing; after that, only without reduce motion.
            animate = !BahrTheme.reducedMotion,
        )
    return TripMapScene(markers = markers, route = route, camera = camera)
}

/** "Abdel Moneim Riad — 05:00" (the time kept left-to-right in Arabic), or the place alone. */
@Composable
private fun departureLabel(marker: DepartureMarker): String =
    marker.time?.let { stringResource(Res.string.map_departure_label, marker.point.placeName, BahrFormat.ltr(it)) }
        ?: marker.point.placeName

internal val MapPinDto.isSoldOut: Boolean get() = nextDeparture?.soldOut == true

private const val DEPARTURE_ID_PREFIX = "departure:"
private const val DISTANCE_ID = "distance"
private const val Z_SOLD_OUT = 0f
private const val Z_PIN = 1f
private const val Z_SELECTED = 2f
private const val Z_DEPARTURE = 3f
private const val Z_DISTANCE = 4f
