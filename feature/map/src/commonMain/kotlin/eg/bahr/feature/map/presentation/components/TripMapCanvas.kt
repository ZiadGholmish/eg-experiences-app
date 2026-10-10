package eg.bahr.feature.map.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.feature.map.presentation.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The map without a map: [scene]'s markers and line on a plain ground, framed on its camera's bounds.
 * Drawn when the device has no Maps API key (a checkout without one still runs, "pins on a blank
 * map"), and by the screenshot tests, where neither Google map can run. The projection is a simple
 * equirectangular one, scaled by the cosine of the latitude: right enough for a country-sized view.
 *
 * Pins are tappable, the ground clears the selection, like the real map.
 */
@Composable
internal fun TripMapCanvas(
    scene: TripMapScene,
    onPinClick: (id: String) -> Unit,
    onMapClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val ground = BahrTheme.colors.surfaceDim
    val noRipple = remember { MutableInteractionSource() }
    // Left/top, not start: a map has no reading direction, so nothing on it mirrors in Arabic.
    BoxWithConstraints(
        contentAlignment = AbsoluteAlignment.TopLeft,
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .background(ground)
                .clickable(interactionSource = noRipple, indication = null, onClick = onMapClick),
    ) {
        val pad =
            with(density) {
                Frame(
                    left = contentPadding.calculateLeftPadding(direction).toPx() + scene.camera.edgePaddingPx,
                    top = contentPadding.calculateTopPadding().toPx() + scene.camera.edgePaddingPx,
                    right = contentPadding.calculateRightPadding(direction).toPx() + scene.camera.edgePaddingPx,
                    bottom = contentPadding.calculateBottomPadding().toPx() + scene.camera.edgePaddingPx,
                )
            }
        val projection = Projection(scene.camera.bounds, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), pad)

        scene.route?.let { route ->
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    color = route.color,
                    start = projection.toScreen(route.from),
                    end = projection.toScreen(route.to),
                    strokeWidth = route.widthPx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(route.dashPx, route.gapPx)),
                )
            }
        }
        scene.markers.forEach { marker ->
            val at = projection.toScreen(marker.point)
            val bitmap = marker.image.bitmap
            val topLeft =
                IntOffset(
                    (at.x - bitmap.width * marker.image.anchorX).roundToInt(),
                    (at.y - bitmap.height * marker.image.anchorY).roundToInt(),
                )
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier =
                    Modifier
                        .zIndex(marker.zIndex)
                        .absoluteOffset { topLeft }
                        .semantics { contentDescription = marker.description }
                        .then(
                            if (marker.clickable) {
                                Modifier.clickable(role = Role.Button) { onPinClick(marker.id) }
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}

/** Space kept clear at each side, in pixels (physical sides: the map has no reading direction). */
private data class Frame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Lat/lng to pixels: [bounds] fitted, centred, inside the area [frame] leaves free. */
private class Projection(
    bounds: GeoBounds,
    width: Float,
    height: Float,
    frame: Frame,
) {
    private val xScale = cos(bounds.center.lat * PI / HALF_TURN_DEGREES)
    private val spanX = (bounds.east - bounds.west) * xScale
    private val spanY = bounds.north - bounds.south
    private val freeW = (width - frame.left - frame.right).coerceAtLeast(1f)
    private val freeH = (height - frame.top - frame.bottom).coerceAtLeast(1f)
    private val scale = min(freeW / spanX, freeH / spanY).toFloat()
    private val centerX = frame.left + freeW / 2
    private val centerY = frame.top + freeH / 2
    private val center = bounds.center

    fun toScreen(p: GeoPoint): Offset =
        Offset(
            x = centerX + ((p.lng - center.lng) * xScale).toFloat() * scale,
            // North is up: a larger latitude is higher on screen.
            y = centerY - (p.lat - center.lat).toFloat() * scale,
        )
}

private const val HALF_TURN_DEGREES = 180.0
