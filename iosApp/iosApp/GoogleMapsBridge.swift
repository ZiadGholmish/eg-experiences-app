import ComposeApp
import GoogleMaps
import UIKit

/// The Google Maps SDK for iOS behind the shared map screen (M4-M2). The SDK comes from Swift Package
/// Manager (github.com/googlemaps/ios-maps-sdk); Kotlin has no bindings for it and needs none: it
/// hands over finished marker pictures, colours and coordinates (`TripMapNativeViews` in
/// feature:map), so nothing here decides how the map looks beyond placing them.
enum GoogleMapsSetup {
    /// Provides the API key once, at launch. The key comes from Info.plist (`MAPS_API_KEY`, filled from
    /// `MAPS_API_KEY_IOS` in the gitignored Configuration/Maps.private.xcconfig); it is never logged.
    /// Without one the map screen draws its pins on a plain ground instead (no bridge is handed over).
    static let ready: Bool = {
        let key = (Bundle.main.object(forInfoDictionaryKey: "MAPS_API_KEY") as? String ?? "")
            .trimmingCharacters(in: .whitespaces)
        guard !key.isEmpty else {
            print("[TripMap] No MAPS_API_KEY_IOS in this build: the map shows pins on a plain ground.")
            return false
        }
        return GMSServices.provideAPIKey(key)
    }()

    /// The bridge for `MainViewController`, or nil without a key.
    static func bridge() -> MapTripMapNativeViews? { ready ? GoogleMapsBridge() : nil }
}

final class GoogleMapsBridge: NSObject, MapTripMapNativeViews {
    func createMap(styleJson: String, events: MapTripMapNativeEvents) -> UIView {
        TripMapContainer(styleJson: styleJson, events: events)
    }

    func setPadding(map: UIView, top: Double, left: Double, bottom: Double, right: Double) {
        (map as? TripMapContainer)?.map.padding = UIEdgeInsets(top: top, left: left, bottom: bottom, right: right)
    }

    func setMarkers(map: UIView, markers: [MapTripMapNativeMarker]) {
        (map as? TripMapContainer)?.show(markers: markers)
    }

    func setRoute(map: UIView, route: MapTripMapNativeRoute?) {
        (map as? TripMapContainer)?.show(route: route)
    }

    func fitBounds(
        map: UIView, south: Double, west: Double, north: Double, east: Double, edgePadding: Double, animated: Bool
    ) {
        let bounds = GMSCoordinateBounds(
            coordinate: CLLocationCoordinate2D(latitude: south, longitude: west),
            coordinate: CLLocationCoordinate2D(latitude: north, longitude: east)
        )
        (map as? TripMapContainer)?.fit(bounds, padding: edgePadding, animated: animated)
    }
}

/// The map in a plain container view: the container knows when it first has a size, so a framing asked
/// for before layout (the first one always is) waits for it instead of fitting into a zero-sized view.
private final class TripMapContainer: UIView, GMSMapViewDelegate {
    let map: GMSMapView
    private let events: MapTripMapNativeEvents
    private var markers: [GMSMarker] = []
    private var line: GMSPolyline?
    private var pendingFit: (bounds: GMSCoordinateBounds, padding: Double)?

    /// How many dashes the drive line is cut into. GMSStyleSpans measure in metres, not points, so the
    /// pattern is spread along the line's own length rather than kept at the design's 6/7 pt.
    private static let dashCount = 40.0

    init(styleJson: String, events: MapTripMapNativeEvents) {
        let options = GMSMapViewOptions()
        map = GMSMapView(options: options)
        self.events = events
        super.init(frame: .zero)
        // No map ID anywhere: a map ID makes the SDK ignore a JSON style.
        do {
            map.mapStyle = try GMSMapStyle(jsonString: styleJson)
        } catch {
            // The map still works, just not desaturated: say so instead of failing silently.
            Self.warnStyleOnce(error)
        }
        // The SDK hides the map's own accessibility elements by default; with them shown, VoiceOver
        // reaches each marker and reads its `title` (the pin's title and price, "Sold out" when it is).
        map.accessibilityElementsHidden = false
        map.settings.compassButton = false
        map.settings.myLocationButton = false
        map.settings.rotateGestures = false
        map.settings.tiltGestures = false
        map.settings.indoorPicker = false
        map.isIndoorEnabled = false
        map.delegate = self
        map.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        addSubview(map)
    }

    private static var styleWarned = false

    private static func warnStyleOnce(_ error: Error) {
        guard !styleWarned else { return }
        styleWarned = true
        print("[TripMap] The map style JSON did not parse; the map is drawn unstyled: \(error)")
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    override func layoutSubviews() {
        super.layoutSubviews()
        map.frame = bounds
        if let pending = pendingFit, bounds.width > 0, bounds.height > 0 {
            pendingFit = nil
            fit(pending.bounds, padding: pending.padding, animated: false)
        }
    }

    func show(markers items: [MapTripMapNativeMarker]) {
        markers.forEach { $0.map = nil }
        markers = items.map { item in
            let marker = GMSMarker(position: CLLocationCoordinate2D(latitude: item.lat, longitude: item.lng))
            marker.icon = item.icon
            marker.groundAnchor = CGPoint(x: item.anchorX, y: item.anchorY)
            marker.zIndex = item.zIndex
            marker.isTappable = item.tappable
            marker.userData = item.id
            // What VoiceOver reads. No info window opens: taps are consumed in didTap below.
            marker.title = item.accessibilityLabel
            marker.map = map
            return marker
        }
    }

    func show(route: MapTripMapNativeRoute?) {
        line?.map = nil
        line = nil
        guard let route else { return }
        let path = GMSMutablePath()
        path.add(CLLocationCoordinate2D(latitude: route.fromLat, longitude: route.fromLng))
        path.add(CLLocationCoordinate2D(latitude: route.toLat, longitude: route.toLng))
        let polyline = GMSPolyline(path: path)
        polyline.strokeWidth = route.width
        polyline.strokeColor = route.color
        let metres = GMSGeometryLength(path)
        let period = metres / Self.dashCount
        let dash = period * route.dash / (route.dash + route.gap)
        polyline.spans = GMSStyleSpans(
            path,
            [GMSStrokeStyle.solidColor(route.color), GMSStrokeStyle.solidColor(.clear)],
            [NSNumber(value: dash), NSNumber(value: period - dash)],
            .rhumb
        )
        polyline.map = map
        line = polyline
    }

    func fit(_ target: GMSCoordinateBounds, padding: Double, animated: Bool) {
        guard bounds.width > 0, bounds.height > 0 else {
            pendingFit = (target, padding)
            return
        }
        let update = GMSCameraUpdate.fit(target, withPadding: padding)
        if animated { map.animate(with: update) } else { map.moveCamera(update) }
    }

    func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
        if let id = marker.userData as? String { events.onMarkerTap(id: id) }
        // Consumed: no info window, and the camera stays where the user put it.
        return true
    }

    func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) {
        events.onMapTap()
    }
}
