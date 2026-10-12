package eg.bahr.feature.map.di

import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIView

/**
 * The Google Maps SDK for iOS, as the map screen uses it (M4-M2). Implemented in Swift
 * (`iosApp/iosApp/GoogleMapsBridge.swift`), where the SDK lives (Swift Package Manager): Kotlin has
 * no bindings for it, and none are needed, because the bridge only takes plain values. Every marker
 * arrives as a finished picture and every colour as a `UIColor`, all drawn and chosen in Kotlin from
 * the theme, so Swift makes no design decision. The app hands an instance to `startApp`,
 * which registers it with Koin; with none (no API key), the map draws its plain fallback.
 *
 * Public, and in `di/`, because Swift implements it: the app's framework header names it through
 * `startApp`'s signature (composeApp's MainViewController.kt), without the feature module being exported.
 */
interface TripMapNativeViews : TripMapNativePanning {
    /**
     * A new map view, styled with [styleJson] (a Google JSON style), standing at [start]'s camera from its
     * first frame and showing [start]'s placeholder colour until its tiles arrive; taps go to [events].
     */
    fun createMap(
        styleJson: String,
        events: TripMapNativeEvents,
        start: TripMapNativeStart,
    ): UIView

    /**
     * Space the screen's chrome covers at each edge, in points: the camera and Google's logo keep clear
     * of it. Once the map has been framed, the camera then moves by ([shiftX], [shiftY]) points, worked
     * out in Kotlin so the map keeps still (M4-M9); before that, the shift is ignored.
     */
    fun setPadding(
        map: UIView,
        top: Double,
        left: Double,
        bottom: Double,
        right: Double,
        shiftX: Double,
        shiftY: Double,
    )

    /**
     * Makes [map]'s markers [markers], matched by id: a marker already there is updated in place, so a
     * new selection does not redraw the rest. A marker whose [TripMapNativeMarker.growFrom] is below 1
     * and was not growing yet springs from that share of its size up to full (M4-M9).
     */
    fun setMarkers(
        map: UIView,
        markers: List<TripMapNativeMarker>,
    )

    /**
     * Frames the box inside the padding plus [edgePadding] points, at once (the map's one framing, M4-M9).
     * Before the view has a size, the implementation keeps the request and applies it once it has one.
     */
    fun fitBounds(
        map: UIView,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        edgePadding: Double,
    )
}

/**
 * The rest of [TripMapNativeViews] (M4-M9 review #2): where a point is on screen, and a vertical pan.
 * Kotlin decides from these whether and how far to pan a picked pin clear of its card.
 */
interface TripMapNativePanning {
    /** How far [lat], [lng] is drawn from [map]'s top, in points; NaN before the map has been framed. */
    fun pointY(
        map: UIView,
        lat: Double,
        lng: Double,
    ): Double

    /**
     * Moves [map]'s content up by [dy] points: over [durationSeconds] on [start]'s easing curve, or at
     * once when it is 0.
     */
    fun pan(
        map: UIView,
        dy: Double,
        durationSeconds: Double,
    )
}

/** Taps on the map, reported by the Swift bridge. */
interface TripMapNativeEvents {
    /** A tappable marker; [id] is the one it was given. */
    fun onMarkerTap(id: String)

    /** The map itself, away from any marker. */
    fun onMapTap()
}

/**
 * How a new map starts (M4-M9): its camera ([lat], [lng], [zoom], Google's zoom) already framed on the
 * pins, so it never shows the world first; the colour shown until its tiles load ([placeholder]); and
 * the selected pin's spring ([springStiffness], [springDampingRatio], mass 1) and the pan's curve
 * ([easeX1]..[easeY2]), the shared motion tokens.
 */
class TripMapNativeStart(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val placeholder: UIColor,
    val springStiffness: Double,
    val springDampingRatio: Double,
    /** The motion tokens' Standard curve (cubic-bezier x1, y1, x2, y2), for the pan. */
    val easeX1: Double,
    val easeY1: Double,
    val easeX2: Double,
    val easeY2: Double,
)

/**
 * One marker: [icon] drawn with the point ([anchorX], [anchorY] as fractions of its size) on [lat], [lng].
 * [growFrom] below 1: the selected pin, whose [icon] is drawn grown; it springs up from that share of
 * its size, around its anchor, when it becomes selected. 1: no spring (any other marker, or reduce motion).
 */
class TripMapNativeMarker(
    val id: String,
    val lat: Double,
    val lng: Double,
    val icon: UIImage,
    val anchorX: Double,
    val anchorY: Double,
    val zIndex: Int,
    val tappable: Boolean,
    val accessibilityLabel: String,
    val growFrom: Double,
)
