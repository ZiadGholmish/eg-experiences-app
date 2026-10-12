package eg.bahr.feature.map.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.feature.map.presentation.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * The map itself, one API for both platforms (M4-M2, user decision 2026-10-10: Google Maps on both):
 * Android draws it with `maps-compose`, iOS with the Google Maps SDK for iOS behind a Swift bridge
 * (`di/TripMapNativeViews`) in a UIKit interop view. Everything on top of it (the legend, a pin's card)
 * is ordinary Compose, drawn by the screen.
 *
 * Without an API key on the device (a build from a checkout with no key), both platforms draw
 * [TripMapCanvas] instead: the same pins on a plain ground, so the screen still works. The
 * screenshot tests draw that too, since neither SDK view runs under Robolectric.
 *
 * [contentPadding] is the space the screen's chrome covers at the map's edges: the camera frames
 * inside it, and the Google logo (which must stay visible) sits clear of it.
 */
@Composable
internal expect fun TripMap(
    scene: TripMapScene,
    onPinClick: (id: String) -> Unit,
    onMapClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
)

/**
 * Everything the map shows, resolved to pixels and colours already, so the two platform maps only
 * place it. [style] is the desaturated map style ([MapStyle]).
 */
@Immutable
internal data class TripMapScene(
    val markers: List<MapMarker>,
    val camera: MapCamera,
    val style: String = MapStyle.DESATURATED,
    /** False under reduce motion: the selected pin is simply big, with no spring (M4-M9). */
    val motion: Boolean = true,
    /** The picked pin to keep clear of its own card, once that card's height is known (M4-M9). */
    val reveal: PinReveal? = null,
)

/**
 * The picked pin, to be kept clear of its card (M4-M9 review #2). Picking a pin does not re-frame the
 * map, and the card's padding is compensated so nothing moves; a pin in the band the card covers
 * would stay hidden under its own card. So once the card's height is known ([cardHeightPx], pixels,
 * the space it covers at the map's bottom), each map pans its content up just enough that the pin's
 * anchor sits [clearancePx] above the card's top (the picture under the anchor and a small margin),
 * smoothly on [BahrMotion.Medium] (instant under reduce motion), and not at all when the pin is
 * already clear. A new value (another pin, or the card measured again) is a new request; whether it
 * is acted on is [shouldReveal].
 */
@Immutable
internal data class PinReveal(
    val id: String,
    val point: GeoPoint,
    val clearancePx: Float,
    val cardHeightPx: Int,
)

/**
 * Whether a map pans for [reveal] now: only for a request it has not handled yet. The maps' effects
 * run again for other reasons with the same request (Android's on every padding change, iOS's and the
 * canvas's on a new framing), and must not pan a second time. A framing and a request never meet:
 * the map is framed only when it is read, which clears the selection (M4-M9 review #8; with the drive
 * and "All trips" removed on 2026-10-12, picking a pin never re-frames the map). All three maps
 * decide through this, so they stay in step.
 */
internal fun shouldReveal(
    reveal: PinReveal?,
    lastHandled: PinReveal?,
): Boolean = reveal != null && reveal != lastHandled

/**
 * How far (pixels, or points on iOS: any one unit) a map's content must move up so a point drawn at
 * [anchorY] from the map's top sits [clearance] above the chrome covering its bottom [bottomInset] of
 * [height]; 0 when it already does. A pan never moves the map down.
 */
internal fun revealShift(
    anchorY: Float,
    height: Float,
    bottomInset: Float,
    clearance: Float,
): Float = (anchorY + clearance - (height - bottomInset)).coerceAtLeast(0f)

/**
 * The camera shift (x, y; positive moves the camera right and down, the content left and up) that
 * cancels a padding change from [before] to [after]: a Google map re-centres its camera target in the
 * padded area, which moves it by half the change; shifting the camera by that half keeps every pin
 * where it was (M4-M9: nothing under the card moves). Physical sides, one unit throughout.
 */
internal fun keepStillShift(
    before: MapInsets,
    after: MapInsets,
): Pair<Float, Float> {
    val dx = ((after.left - before.left) - (after.right - before.right)) / 2f
    val dy = ((after.top - before.top) - (after.bottom - before.bottom)) / 2f
    return dx to dy
}

/**
 * One marker: a trip's price pin. [image] is drawn with its anchor on [point]. Only [clickable]
 * markers react to a tap; [description] is what a
 * screen reader says for it.
 *
 * [growFrom] below 1 marks the selected pin (M4-M9): [image] is drawn at its grown size
 * ([BahrMotion.PinSelectedScale]), and when it becomes selected each platform springs it from
 * [growFrom] of that size (its unselected size) up to full, around its anchor.
 */
@Immutable
internal data class MapMarker(
    val id: String,
    val point: GeoPoint,
    val image: MarkerImage,
    val zIndex: Float,
    val clickable: Boolean,
    val description: String,
    val growFrom: Float = 1f,
)

/**
 * A marker's picture: [bitmap] at the device's density, and the point of it that sits on the map
 * position, as fractions of its width ([anchorX]) and height ([anchorY]).
 */
@Immutable
internal class MarkerImage(
    val bitmap: ImageBitmap,
    val anchorX: Float,
    val anchorY: Float,
)

/**
 * Where the camera should be: [bounds] fitted inside the map's content padding plus [edgePaddingPx].
 * A map is framed once, when it is created; [id] tells one read of the map from the next.
 */
@Immutable
internal data class MapCamera(
    val id: Int,
    val bounds: GeoBounds,
    val edgePaddingPx: Int,
)

/**
 * The share of its picture's size to draw [marker] at right now (M4-M9): 1 at rest; for a pin that
 * has just become the selected one ([MapMarker.growFrom] below 1), from its unselected size up to 1
 * on [BahrMotion]'s pin spring, so it grows a little with an overshoot. Under reduce motion
 * ([TripMapScene.motion] false) it is simply 1. Android's map and the plain-ground map use it; iOS
 * runs the same spring natively in the Swift bridge.
 */
@Composable
internal fun animatedPinScale(
    marker: MapMarker,
    motion: Boolean,
): Float {
    val grows = motion && marker.growFrom < 1f
    // A fresh animation per selection, already at the start in the first frame it is drawn in.
    val scale = remember(marker.id, grows) { Animatable(if (grows) marker.growFrom else 1f) }
    LaunchedEffect(scale) {
        if (grows) scale.animateTo(1f, spring(BahrMotion.PinSpringDampingRatio, BahrMotion.PinSpringStiffness))
    }
    return scale.value
}

/**
 * Where a map's camera stands: [target] in the middle of the padded area at [zoom] (Google's zoom: the
 * world is [WORLD_SIZE_AT_ZOOM_0] dp, or points on iOS, wide at zoom 0, and twice as wide per level).
 */
@Immutable
internal data class CameraPose(
    val target: GeoPoint,
    val zoom: Float,
)

/** Space kept clear at each side of a map, in dp (physical sides: the map has no reading direction). */
@Immutable
internal data class MapInsets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * The camera that frames [bounds] in a [width] x [height] dp map whose chrome covers [insets], with
 * [edge] dp more on every side: what Google's own fit-to-bounds works out, done here so a map can be
 * created already framed (M4-M9: no world view first, and no wait for the map's first layout).
 * Web Mercator, as both Google SDKs draw. With no room left (a tiny or landscape view under a big
 * font), the box's centre at [FALLBACK_ZOOM], about all of Egypt.
 */
internal fun cameraToFit(
    bounds: GeoBounds,
    width: Float,
    height: Float,
    insets: MapInsets,
    edge: Float,
): CameraPose {
    val west = mercatorX(bounds.west)
    val east = mercatorX(bounds.east)
    val north = mercatorY(bounds.north)
    val south = mercatorY(bounds.south)
    val target = GeoPoint(lat = latitudeAt((north + south) / 2), lng = (bounds.west + bounds.east) / 2)
    val freeWidth = width - insets.left - insets.right - 2 * edge
    val freeHeight = height - insets.top - insets.bottom - 2 * edge
    if (freeWidth <= 0f || freeHeight <= 0f) return CameraPose(target, FALLBACK_ZOOM)
    val zoomX = log2(freeWidth / (WORLD_SIZE_AT_ZOOM_0 * (east - west)))
    val zoomY = log2(freeHeight / (WORLD_SIZE_AT_ZOOM_0 * (south - north)))
    return CameraPose(target, min(zoomX, zoomY).toFloat().coerceIn(MIN_ZOOM, MAX_ZOOM))
}

/** A longitude as a share of the world's width, 0 at the antimeridian's west side. */
private fun mercatorX(lng: Double): Double = (lng + HALF_TURN) / FULL_TURN

/** A latitude as a share of the world's height, 0 at the top (Web Mercator). */
private fun mercatorY(lat: Double): Double {
    val radians = lat * PI / HALF_TURN
    return .5 - ln(tan(PI / 4 + radians / 2)) / (2 * PI)
}

/** The latitude at a share of the world's height: the inverse of [mercatorY]. */
private fun latitudeAt(y: Double): Double = (2 * atan(exp((.5 - y) * 2 * PI)) - PI / 2) * HALF_TURN / PI

/** Google's world at zoom 0: one 256 dp (Android) or 256 pt (iOS) tile. */
internal const val WORLD_SIZE_AT_ZOOM_0 = 256.0

/** Google's zoom 6: most of Egypt's Nile valley and Delta in view. */
internal const val FALLBACK_ZOOM = 6f
private const val MIN_ZOOM = 2f
private const val MAX_ZOOM = 18f
private const val HALF_TURN = 180.0
private const val FULL_TURN = 360.0

/** A south-west / north-east box. */
@Immutable
internal data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)

    companion object {
        /**
         * The box around [points], at least [MIN_SPAN_DEGREES] across each way: a single pin (or two
         * on the same spot) would otherwise zoom the map to street level.
         */
        fun around(points: List<GeoPoint>): GeoBounds? {
            if (points.isEmpty()) return null
            val south = points.minOf { it.lat }
            val north = points.maxOf { it.lat }
            val west = points.minOf { it.lng }
            val east = points.maxOf { it.lng }
            val halfLat = max(north - south, MIN_SPAN_DEGREES) / 2
            val halfLng = max(east - west, MIN_SPAN_DEGREES) / 2
            val midLat = (south + north) / 2
            val midLng = (west + east) / 2
            return GeoBounds(midLat - halfLat, midLng - halfLng, midLat + halfLat, midLng + halfLng)
        }

        /** About 11 km: a lone pin is shown with its town around it. */
        const val MIN_SPAN_DEGREES = .1
    }
}

/**
 * The map's look: the handoff desaturates its tiles (`saturate(.55) contrast(.95) brightness(1.04)`,
 * map.html). A Google JSON style can only shift saturation and lightness, not contrast, so it takes
 * the first and last. Points of interest and transit are hidden: their icons compete with the price
 * pins, which are the point of the screen. No hex colours, so no design literal; and no map ID is
 * set anywhere, because a map ID makes Google ignore a JSON style.
 */
internal object MapStyle {
    /** `saturate(.55)`: Google's saturation runs -100..100, so 55% is -45. */
    private const val SATURATION = -45

    /** `brightness(1.04)`: Google's lightness runs -100..100. */
    private const val LIGHTNESS = 4

    const val DESATURATED: String =
        """[{"stylers":[{"saturation":$SATURATION},{"lightness":$LIGHTNESS}]},""" +
            """{"featureType":"poi","stylers":[{"visibility":"off"}]},""" +
            """{"featureType":"transit","stylers":[{"visibility":"off"}]}]"""
}
