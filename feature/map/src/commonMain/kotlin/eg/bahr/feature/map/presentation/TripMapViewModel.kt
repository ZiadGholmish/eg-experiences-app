package eg.bahr.feature.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.map.data.TripMapRepository
import eg.bahr.feature.map.model.MapPinDto
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
 * [selectedSlug] is the pin whose card is open. [camera] is the framing the map should show: every
 * pin, asked for once per read of the map (on load, and after a Retry that reads it). Picking a pin
 * never re-frames the map (M4-M9; the "Show the drive" and "All trips" toggles were removed by user
 * decision 2026-10-12).
 */
internal data class TripMapUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val map: TripMapDto? = null,
    val selectedSlug: String? = null,
    val camera: CameraRequest = CameraRequest(id = 0),
) {
    val selectedPin: MapPinDto? get() = selectedSlug?.let { slug -> map?.pins?.firstOrNull { it.slug == slug } }

    /** Loaded, and nothing to pin. */
    val isEmpty: Boolean get() = map != null && map.pins.isEmpty()
}

/** A request to frame every pin (one per read of the map); a new [id] is a new request. */
internal data class CameraRequest(
    val id: Int,
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

    /** A pin was tapped: its card opens. The map is not re-framed. */
    fun selectPin(slug: String) {
        _uiState.update { state ->
            if (state.map?.pins?.none { it.slug == slug } != false) return@update state
            state.copy(selectedSlug = slug)
        }
    }

    /** The map itself (not a pin) or the card's close button: the card goes. */
    fun clearSelection() {
        _uiState.update { it.copy(selectedSlug = null) }
    }

    private fun load() {
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, error = null) }
                when (val result = repository.tripMap()) {
                    is AppResult.Success ->
                        _uiState.update {
                            // A fresh read starts framed on every pin, as on first open.
                            it.copy(loading = false, map = result.data, selectedSlug = null).requestCamera()
                        }

                    is AppResult.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
                }
            }
    }

    private fun TripMapUiState.requestCamera() = copy(camera = CameraRequest(camera.id + 1))
}

/** The points the camera frames: every pin. */
internal fun TripMapUiState.cameraPoints(): List<GeoPoint> = map?.pins.orEmpty().map { GeoPoint(it.lat, it.lng) }

/** A WGS84 position. */
internal data class GeoPoint(
    val lat: Double,
    val lng: Double,
)
