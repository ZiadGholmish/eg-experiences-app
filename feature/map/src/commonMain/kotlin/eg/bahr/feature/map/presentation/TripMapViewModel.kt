package eg.bahr.feature.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.map.data.TripMapRepository
import eg.bahr.feature.map.model.MapPinDto
import eg.bahr.feature.map.model.MapPlaceDto
import eg.bahr.feature.map.model.TripMapDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The map screen (M4-M2).
 *
 * [selectedSlug] is the pin whose card is open. [showsDrive] is the "Show the drive from Cairo"
 * toggle: the dark departure marker, and a dashed line from it to the selected pin. [camera] is the
 * latest framing the map should move to; its [CameraRequest.id] grows with each request, so the same
 * framing asked twice (tap "All trips" again) still moves the map back.
 */
internal data class TripMapUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val map: TripMapDto? = null,
    val selectedSlug: String? = null,
    val showsDrive: Boolean = false,
    val camera: CameraRequest = CameraRequest(id = 0, frame = CameraFrame.AllPins),
) {
    val selectedPin: MapPinDto? get() = selectedSlug?.let { slug -> map?.pins?.firstOrNull { it.slug == slug } }

    /** Loaded, and nothing to pin. */
    val isEmpty: Boolean get() = map != null && map.pins.isEmpty()
}

/** What the map frames: every pin, or the drive (departure point and the selected pin). */
internal enum class CameraFrame { AllPins, Drive }

internal data class CameraRequest(
    val id: Int,
    val frame: CameraFrame,
)

/** Reads `GET /trips/map` once per opening: seat counts are hints, so the map never outlives its screen. */
internal class TripMapViewModel(
    private val repository: TripMapRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripMapUiState())
    val uiState: StateFlow<TripMapUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Retry after an error. A tap while a read is already running is ignored, so a burst sends one request. */
    fun retry() {
        if (loadJob?.isActive == true) return
        load()
    }

    /** A pin was tapped: its card opens; with the drive shown, the map frames the new drive. */
    fun selectPin(slug: String) {
        _uiState.update { state ->
            if (state.map?.pins?.none { it.slug == slug } != false) return@update state
            val moved = state.copy(selectedSlug = slug)
            if (state.showsDrive && state.selectedSlug != slug) moved.requestCamera(CameraFrame.Drive) else moved
        }
    }

    /** The map itself (not a pin) or the card's close button: the card goes. */
    fun clearSelection() {
        _uiState.update { it.copy(selectedSlug = null) }
    }

    /** "Show the drive": on frames the drive, off just takes the line and departure marker away. */
    fun toggleDrive() {
        _uiState.update { state ->
            if (state.showsDrive) {
                state.copy(showsDrive = false)
            } else {
                state.copy(showsDrive = true).requestCamera(CameraFrame.Drive)
            }
        }
    }

    /** "All trips": the drive goes, and the map frames every pin again (the prototype's lake view). */
    fun showAllTrips() {
        _uiState.update { it.copy(showsDrive = false).requestCamera(CameraFrame.AllPins) }
    }

    private fun load() {
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, error = null) }
                when (val result = repository.tripMap()) {
                    is AppResult.Success ->
                        _uiState.update {
                            // A fresh read starts framed on every pin, as on first open.
                            it
                                .copy(loading = false, map = result.data, selectedSlug = null, showsDrive = false)
                                .requestCamera(CameraFrame.AllPins)
                        }

                    is AppResult.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
                }
            }
    }

    private fun TripMapUiState.requestCamera(frame: CameraFrame) = copy(camera = CameraRequest(camera.id + 1, frame))
}

// ---------- What the map draws, derived from the state (pure, so the tests read it directly) ----------

/**
 * A departure marker: [time] is the shared bus time of the pins leaving from it, or null when they
 * do not agree or none has one (`MapPin.departTime`'s labelling rule): the marker then reads
 * [placeName] alone.
 */
internal data class DepartureMarker(
    val point: MapPlaceDto,
    val time: String?,
)

/** The dashed drive: from the departure point to the selected pin, labelled with [distanceKm] when known. */
internal data class DriveLine(
    val from: MapPlaceDto,
    val to: MapPinDto,
    val distanceKm: Int?,
)

/**
 * The contract's rule for labelling a departure point: the time of the pins naming it, when every one
 * of them that carries a `departTime` has the same; otherwise (they differ, or none has one) none.
 */
internal fun departureTime(
    pointId: String,
    pins: List<MapPinDto>,
): String? =
    pins
        .filter { it.departurePointId == pointId }
        .mapNotNull { it.departTime }
        .distinct()
        .singleOrNull()

/**
 * The departure markers shown with the drive on: the selected pin's own departure point, or with no
 * pin selected every departure point (today one, Cairo), so the toggle always shows where the bus
 * leaves. None with the drive off.
 */
internal fun TripMapUiState.departureMarkers(): List<DepartureMarker> {
    val data = map ?: return emptyList()
    if (!showsDrive) return emptyList()
    val selected = selectedPin
    val points =
        if (selected != null) {
            listOfNotNull(data.departurePoints.firstOrNull { it.id == selected.departurePointId })
        } else {
            data.departurePoints
        }
    return points.map { DepartureMarker(it, departureTime(it.id, data.pins)) }
}

/**
 * The line, only with the drive on and a pin selected whose departure point the response lists. A
 * pin with no `departurePointId` (or one not in `departurePoints`) has no line; no `distanceKm`, no label.
 */
internal fun TripMapUiState.driveLine(): DriveLine? {
    if (!showsDrive) return null
    val pin = selectedPin ?: return null
    val from = map?.departurePoints?.firstOrNull { it.id == pin.departurePointId } ?: return null
    return DriveLine(from = from, to = pin, distanceKm = pin.distanceKm)
}

/** The points the camera frames for [TripMapUiState.camera]. Never empty while there are pins. */
internal fun TripMapUiState.cameraPoints(): List<GeoPoint> {
    val data = map ?: return emptyList()
    val pins = data.pins.map { GeoPoint(it.lat, it.lng) }
    if (camera.frame == CameraFrame.AllPins) return pins
    val line = driveLine()
    if (line != null) return listOf(GeoPoint(line.from.lat, line.from.lng), GeoPoint(line.to.lat, line.to.lng))
    // The drive with no pin selected: where the buses leave, and every pin.
    return departureMarkers().map { GeoPoint(it.point.lat, it.point.lng) } + pins
}

/** A WGS84 position. */
internal data class GeoPoint(
    val lat: Double,
    val lng: Double,
)
