package eg.bahr.feature.map.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrTween
import eg.bahr.feature.map.presentation.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The map without a map: [scene]'s markers on a plain ground, framed on its camera's bounds.
 * Drawn when the device has no Maps API key (a checkout without one still runs, "pins on a blank
 * map"), and by the screenshot tests, where neither Google map can run. The projection is a simple
 * equirectangular one, scaled by the cosine of the latitude: right enough for a country-sized view.
 *
 * Pins are tappable, the ground clears the selection, like the real map. As on the real maps, the
 * chrome's space is read when the camera is asked to frame ([MapCamera.id]), not on every change:
 * the card opening over the map does not move the pins (M4-M9). The selected pin springs to its
 * grown size ([animatedPinScale]), and a picked pin its card would cover is panned clear of it
 * ([PinReveal]).
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
    val ground = mapGround()
    val noRipple = remember { MutableInteractionSource() }
    // The framing and the chrome as they were when this framing was asked for (the screen draws the map
    // once its chrome is measured); like the platform maps, the canvas moves only on a new request.
    val camera = remember(scene.camera.id) { scene.camera }
    val framedIn = remember(scene.camera.id) { contentPadding }
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
                    left = framedIn.calculateLeftPadding(direction).toPx() + camera.edgePaddingPx,
                    top = framedIn.calculateTopPadding().toPx() + camera.edgePaddingPx,
                    right = framedIn.calculateRightPadding(direction).toPx() + camera.edgePaddingPx,
                    bottom = framedIn.calculateBottomPadding().toPx() + camera.edgePaddingPx,
                )
            }
        val projection = Projection(camera.bounds, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), pad)
        // How far the content has been panned up to keep a picked pin clear of its card; a new framing
        // starts it over, as on the platform maps, which pan by the same rule ([shouldReveal]).
        val pan = remember(camera.id) { Animatable(0f) }
        val height = constraints.maxHeight.toFloat()
        val panSpec = bahrTween<Float>()
        val handled = remember { CanvasHandled() }
        LaunchedEffect(scene.reveal, camera.id) {
            val reveal = scene.reveal
            if (reveal != null && shouldReveal(reveal, handled.reveal)) {
                val shift =
                    revealShift(projection.toScreen(reveal.point).y - pan.value, height, reveal.cardHeightPx.toFloat(), reveal.clearancePx)
                if (shift > 0f) {
                    handled.reveal = reveal
                    pan.animateTo(pan.value + shift, panSpec)
                }
            }
            handled.reveal = reveal
        }
        val at = { point: GeoPoint -> projection.toScreen(point) - Offset(0f, pan.value) }

        scene.markers.forEach { marker ->
            key(marker.id) {
                CanvasMarker(marker, at(marker.point), scene.motion, onPinClick)
            }
        }
    }
}

/**
 * The map's plain ground: what the plain-ground map draws on, and what both Google maps show until
 * their tiles arrive (M4-M9: a calm placeholder, not the SDK's own grey or a flash of the world).
 */
@Composable
@ReadOnlyComposable
internal fun mapGround(): Color = BahrTheme.colors.surfaceDim

/** One marker, its anchor on [at], scaled about the anchor while the selected pin grows. */
@Composable
private fun CanvasMarker(
    marker: MapMarker,
    at: Offset,
    motion: Boolean,
    onPinClick: (id: String) -> Unit,
) {
    val bitmap = marker.image.bitmap
    val topLeft =
        IntOffset(
            (at.x - bitmap.width * marker.image.anchorX).roundToInt(),
            (at.y - bitmap.height * marker.image.anchorY).roundToInt(),
        )
    val scale = animatedPinScale(marker, motion)
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier =
            Modifier
                .zIndex(marker.zIndex)
                .absoluteOffset { topLeft }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(marker.image.anchorX, marker.image.anchorY)
                }.semantics { contentDescription = marker.description }
                .then(
                    if (marker.clickable) {
                        Modifier.clickable(role = Role.Button) { onPinClick(marker.id) }
                    } else {
                        Modifier
                    },
                ),
    )
}

/** The reveal request the canvas last acted on; not state, nothing redraws on it. */
private class CanvasHandled {
    var reveal: PinReveal? = null
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
