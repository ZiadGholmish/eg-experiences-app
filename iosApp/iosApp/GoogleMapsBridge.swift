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

    /// The bridge for `startApp`, or nil without a key.
    static func bridge() -> MapTripMapNativeViews? { ready ? GoogleMapsBridge() : nil }
}

final class GoogleMapsBridge: NSObject, MapTripMapNativeViews {
    func createMap(styleJson: String, events: MapTripMapNativeEvents, start: MapTripMapNativeStart) -> UIView {
        TripMapContainer(styleJson: styleJson, events: events, start: start)
    }

    func setPadding(map: UIView, top: Double, left: Double, bottom: Double, right: Double, shiftX: Double, shiftY: Double) {
        (map as? TripMapContainer)?.setPadding(
            UIEdgeInsets(top: top, left: left, bottom: bottom, right: right), shift: CGPoint(x: shiftX, y: shiftY)
        )
    }

    func setMarkers(map: UIView, markers: [MapTripMapNativeMarker]) {
        (map as? TripMapContainer)?.show(markers: markers)
    }

    func pointY(map: UIView, lat: Double, lng: Double) -> Double {
        (map as? TripMapContainer)?.pointY(CLLocationCoordinate2D(latitude: lat, longitude: lng)) ?? .nan
    }

    func pan(map: UIView, dy: Double, durationSeconds: Double) {
        (map as? TripMapContainer)?.pan(dy: dy, duration: durationSeconds)
    }

    func fitBounds(
        map: UIView, south: Double, west: Double, north: Double, east: Double, edgePadding: Double
    ) {
        let bounds = GMSCoordinateBounds(
            coordinate: CLLocationCoordinate2D(latitude: south, longitude: west),
            coordinate: CLLocationCoordinate2D(latitude: north, longitude: east)
        )
        (map as? TripMapContainer)?.fit(bounds, padding: edgePadding)
    }
}

/// The map in a plain container view: the container knows when it first has a size, so a framing asked
/// for before layout (the first one always is) waits for it instead of fitting into a zero-sized view.
private final class TripMapContainer: UIView, GMSMapViewDelegate {
    let map: GMSMapView
    private let events: MapTripMapNativeEvents
    /// The markers on the map, by the id Kotlin gave them: updated in place (M4-M9).
    private var markers: [String: GMSMarker] = [:]
    private var pendingFit: (bounds: GMSCoordinateBounds, padding: Double)?
    /// The selected pin's spring, from the shared motion tokens.
    private let springStiffness: Double
    private let springDamping: Double
    /// How long a marker view whose picture changed is tracked, so the SDK draws the new picture.
    private static let redrawWindow: TimeInterval = 0.1
    /// Set by the first framing: from then on a padding change keeps the map still.
    private var framed = false
    /// The pan's curve, the shared motion tokens' Standard easing.
    private let panTiming: CAMediaTimingFunction

    init(styleJson: String, events: MapTripMapNativeEvents, start: MapTripMapNativeStart) {
        let options = GMSMapViewOptions()
        // Framed on the pins from the first frame, not on the world at zoom 0, and on the app's map
        // ground until the tiles arrive (M4-M9). Both come from Kotlin.
        options.camera = GMSCameraPosition(latitude: start.lat, longitude: start.lng, zoom: Float(start.zoom))
        options.backgroundColor = start.placeholder
        map = GMSMapView(options: options)
        self.events = events
        springStiffness = start.springStiffness
        // UIKit's spring takes a damping coefficient; the token is a ratio of the critical one (mass 1).
        springDamping = 2 * start.springDampingRatio * start.springStiffness.squareRoot()
        panTiming = CAMediaTimingFunction(
            controlPoints: Float(start.easeX1), Float(start.easeY1), Float(start.easeX2), Float(start.easeY2)
        )
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
            fit(pending.bounds, padding: pending.padding)
        }
    }

    /// The chrome's space at the edges. Once framed, the camera moves by `shift`, which Kotlin works
    /// out (`keepStillShift`, shared with Android) so the map keeps still under a card that opens or
    /// closes (M4-M9); Google's logo still moves with the padding, clear of the card.
    func setPadding(_ padding: UIEdgeInsets, shift: CGPoint) {
        guard padding != map.padding else { return }
        map.padding = padding
        guard framed, shift != .zero else { return }
        map.moveCamera(GMSCameraUpdate.scrollBy(x: shift.x, y: shift.y))
    }

    /// Where a coordinate is drawn, in points from the top; NaN until the first framing.
    func pointY(_ coordinate: CLLocationCoordinate2D) -> Double {
        guard framed else { return .nan }
        return Double(map.projection.point(for: coordinate).y)
    }

    /// Moves the content up by `dy` points (the camera down): over `duration` on the shared curve, or
    /// at once when it is 0. Kotlin decides whether and how far (a picked pin kept clear of its card).
    func pan(dy: Double, duration: Double) {
        let update = GMSCameraUpdate.scrollBy(x: 0, y: dy)
        guard duration > 0 else {
            map.moveCamera(update)
            return
        }
        CATransaction.begin()
        CATransaction.setAnimationDuration(duration)
        CATransaction.setAnimationTimingFunction(panTiming)
        map.animate(with: update)
        CATransaction.commit()
    }

    /// Updates the markers in place by id; a marker that starts growing springs (M4-M9).
    func show(markers items: [MapTripMapNativeMarker]) {
        let wanted = Set(items.map(\.id))
        for (id, marker) in markers where !wanted.contains(id) {
            marker.map = nil
            markers[id] = nil
        }
        for item in items {
            let marker = markers[item.id] ?? GMSMarker()
            marker.position = CLLocationCoordinate2D(latitude: item.lat, longitude: item.lng)
            marker.groundAnchor = CGPoint(x: item.anchorX, y: item.anchorY)
            marker.zIndex = item.zIndex
            marker.isTappable = item.tappable
            marker.userData = item.id
            // What VoiceOver reads. No info window opens: taps are consumed in didTap below.
            marker.title = item.accessibilityLabel
            if item.growFrom < 1 {
                if let pin = marker.iconView as? GrowingPin {
                    // Still selected with a new picture: re-laid out to its size, and drawn again once
                    // (after its spring the SDK no longer tracks the view).
                    if pin.image !== item.icon {
                        pin.show(item.icon, anchor: marker.groundAnchor)
                        if !pin.growing {
                            marker.tracksViewChanges = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + Self.redrawWindow) {
                                if !pin.growing { marker.tracksViewChanges = false }
                            }
                        }
                    }
                } else {
                    let pin = GrowingPin(image: item.icon, anchor: marker.groundAnchor)
                    marker.icon = nil
                    marker.iconView = pin
                    // Redrawn every frame only while the spring runs.
                    marker.tracksViewChanges = true
                    pin.grow(from: item.growFrom, stiffness: springStiffness, damping: springDamping) {
                        marker.tracksViewChanges = false
                    }
                }
            } else {
                marker.iconView = nil
                marker.icon = item.icon
            }
            if markers[item.id] == nil {
                marker.map = map
                markers[item.id] = marker
            }
        }
    }

    /// The map's one framing, at once (M4-M9: picking a pin never re-frames the map).
    func fit(_ target: GMSCoordinateBounds, padding: Double) {
        guard bounds.width > 0, bounds.height > 0 else {
            pendingFit = (target, padding)
            return
        }
        map.moveCamera(GMSCameraUpdate.fit(target, withPadding: padding))
        framed = true
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

/// The selected pin as a marker view (M4-M9): its picture (drawn grown by Kotlin) springs up from its
/// unselected share around the anchor, the point on the map. A marker view is clipped to its bounds, so
/// the picture sits in a slightly larger box with room for the spring's overshoot, placed so the anchor
/// is the same fraction of the box as of the picture: the marker's ground anchor stays as Kotlin set it.
private final class GrowingPin: UIView {
    /// The spring overshoots its target by under 2% (medium-bouncy damping over a 12% grow); 5% room
    /// keeps the overshoot from being cut off.
    private static let overshootRoom: CGFloat = 1.05

    private let picture = UIImageView()

    var image: UIImage? { picture.image }

    /// True while the spring runs (the SDK tracks the view then anyway).
    private(set) var growing = false

    init(image: UIImage, anchor: CGPoint) {
        super.init(frame: .zero)
        addSubview(picture)
        show(image, anchor: anchor)
    }

    /// Shows [image], the box sized for it with room, the anchor where it would be without room.
    func show(_ image: UIImage, anchor: CGPoint) {
        let size = image.size
        let room = Self.overshootRoom
        let transform = picture.transform
        picture.transform = .identity
        frame = CGRect(origin: .zero, size: CGSize(width: size.width * room, height: size.height * room))
        picture.image = image
        // Scale about the anchor, which sits where it would in a box without room.
        picture.layer.anchorPoint = anchor
        picture.frame = CGRect(
            x: anchor.x * size.width * (room - 1),
            y: anchor.y * size.height * (room - 1),
            width: size.width,
            height: size.height
        )
        picture.transform = transform
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    /// From [from] of full size up to full, on a spring of the given stiffness and damping (mass 1).
    func grow(from: Double, stiffness: Double, damping: Double, completion: @escaping () -> Void) {
        picture.transform = CGAffineTransform(scaleX: from, y: from)
        let spring = UISpringTimingParameters(mass: 1, stiffness: stiffness, damping: damping, initialVelocity: .zero)
        // The duration is ignored for a spring given by mass, stiffness and damping: it runs until it settles.
        let animator = UIViewPropertyAnimator(duration: 0, timingParameters: spring)
        growing = true
        animator.addAnimations { self.picture.transform = .identity }
        animator.addCompletion { _ in
            self.growing = false
            completion()
        }
        animator.startAnimation()
    }
}
