package eg.bahr.feature.map.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.departure_sold_out
import eg.bahr.core.localization.generated.resources.format_pair
import eg.bahr.feature.map.model.MapPinDto
import eg.bahr.feature.map.presentation.GeoPoint
import eg.bahr.feature.map.presentation.TripMapUiState
import eg.bahr.feature.map.presentation.cameraPoints
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The state as the map draws it: pins (in their tone, grey when sold out, ringed and grown when
 * selected) and where the camera goes.
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
                val selected = pin.slug == state.selectedSlug
                // The selected pin is drawn grown, so it stays sharp at its full size (M4-M9).
                painter.pin(prices[i], colors, selected = selected, scale = if (selected) BahrMotion.PinSelectedScale else 1f)
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
                growFrom = if (selected) 1f / BahrMotion.PinSelectedScale else 1f,
            )
        }

    val bounds = GeoBounds.around(state.cameraPoints()) ?: return null
    // The camera frames the points the pins stand on; a pin's picture rises above its point, so the
    // tallest one is kept inside the frame too, or a pin at the top edge is cut off. Taken at its grown
    // size, whichever pin is picked, so a picked pin at the top is not cut either, and picking a pin
    // does not change the frame (M4-M9, nothing under the card moves).
    val tallest = pins.maxOfOrNull { (it.image.bitmap.height * it.growFrom * BahrMotion.PinSelectedScale).roundToInt() } ?: 0
    val camera =
        MapCamera(
            id = state.camera.id,
            bounds = bounds,
            edgePaddingPx = with(density) { BahrSpacing.lg.roundToPx() } + tallest,
        )
    return TripMapScene(markers = pins, camera = camera, motion = !BahrTheme.reducedMotion)
}

internal val MapPinDto.isSoldOut: Boolean get() = nextDeparture?.soldOut == true

private const val Z_SOLD_OUT = 0f
private const val Z_PIN = 1f
private const val Z_SELECTED = 2f
