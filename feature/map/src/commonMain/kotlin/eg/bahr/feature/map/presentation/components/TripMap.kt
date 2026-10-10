package eg.bahr.feature.map.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import eg.bahr.feature.map.presentation.GeoPoint
import kotlin.math.max

/**
 * The map itself, one API for both platforms (M4-M2, user decision 2026-10-10: Google Maps on both):
 * Android draws it with `maps-compose`, iOS with the Google Maps SDK for iOS behind a Swift bridge
 * (`di/TripMapNativeViews`) in a UIKit interop view. Everything on top of it (toggles, legend, card)
 * is ordinary Compose, drawn by the screen.
 *
 * Without an API key on the device (a build from a checkout with no key), both platforms draw
 * [TripMapCanvas] instead: the same pins and line on a plain ground, so the screen still works. The
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
    val route: MapRoute?,
    val camera: MapCamera,
    val style: String = MapStyle.DESATURATED,
)

/**
 * One marker: a price pin, the departure marker or the drive's distance tag. [image] is drawn with
 * its anchor on [point]. Only [clickable] markers react to a tap (the pins); [description] is what a
 * screen reader says for it.
 */
@Immutable
internal data class MapMarker(
    val id: String,
    val point: GeoPoint,
    val image: MarkerImage,
    val zIndex: Float,
    val clickable: Boolean,
    val description: String,
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

/** The dashed drive line, in pixels at the device's density. */
@Immutable
internal data class MapRoute(
    val from: GeoPoint,
    val to: GeoPoint,
    val color: Color,
    val widthPx: Float,
    val dashPx: Float,
    val gapPx: Float,
)

/**
 * Where the camera should be: [bounds] fitted inside the map's content padding plus [edgePaddingPx].
 * A new [id] is a new request; [animate] is false on the first framing and with reduce motion on.
 */
@Immutable
internal data class MapCamera(
    val id: Int,
    val bounds: GeoBounds,
    val edgePaddingPx: Int,
    val animate: Boolean,
)

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
