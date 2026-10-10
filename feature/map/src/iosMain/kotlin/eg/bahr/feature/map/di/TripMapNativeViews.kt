package eg.bahr.feature.map.di

import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIView

/**
 * The Google Maps SDK for iOS, as the map screen uses it (M4-M2). Implemented in Swift
 * (`iosApp/iosApp/GoogleMapsBridge.swift`), where the SDK lives (Swift Package Manager): Kotlin has
 * no bindings for it, and none are needed, because the bridge only takes plain values. Every marker
 * arrives as a finished picture and every colour as a `UIColor`, all drawn and chosen in Kotlin from
 * the theme, so Swift makes no design decision. The app hands an instance to `MainViewController`,
 * which registers it with Koin; with none (no API key), the map draws its plain fallback.
 *
 * Public, and in `di/`, because Swift implements it: the app's framework header names it through
 * `MainViewController`'s signature, without the feature module being exported.
 */
interface TripMapNativeViews {
    /** A new map view, styled with [styleJson] (a Google JSON style); taps go to [events]. */
    fun createMap(
        styleJson: String,
        events: TripMapNativeEvents,
    ): UIView

    /** Space the screen's chrome covers at each edge, in points: the camera and Google's logo keep clear of it. */
    fun setPadding(
        map: UIView,
        top: Double,
        left: Double,
        bottom: Double,
        right: Double,
    )

    /** Replaces every marker on [map] with [markers]. */
    fun setMarkers(
        map: UIView,
        markers: List<TripMapNativeMarker>,
    )

    /** The dashed drive line, or none. */
    fun setRoute(
        map: UIView,
        route: TripMapNativeRoute?,
    )

    /**
     * Frames the box inside the padding plus [edgePadding] points. Before the view has a size, the
     * implementation keeps the request and applies it once it has one.
     */
    fun fitBounds(
        map: UIView,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        edgePadding: Double,
        animated: Boolean,
    )
}

/** Taps on the map, reported by the Swift bridge. */
interface TripMapNativeEvents {
    /** A tappable marker; [id] is the one it was given. */
    fun onMarkerTap(id: String)

    /** The map itself, away from any marker. */
    fun onMapTap()
}

/** One marker: [icon] drawn with the point ([anchorX], [anchorY] as fractions of its size) on [lat], [lng]. */
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
)

/** A straight dashed line, its sizes in points. */
class TripMapNativeRoute(
    val fromLat: Double,
    val fromLng: Double,
    val toLat: Double,
    val toLng: Double,
    val color: UIColor,
    val width: Double,
    val dash: Double,
    val gap: Double,
)
