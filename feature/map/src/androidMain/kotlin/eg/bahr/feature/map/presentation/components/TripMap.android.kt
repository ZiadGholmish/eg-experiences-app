package eg.bahr.feature.map.presentation.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.feature.map.presentation.GeoPoint
import kotlin.math.roundToInt

/**
 * Android: `maps-compose`. The key is the manifest's `com.google.android.geo.API_KEY`, filled from
 * `local.properties` at build time (`MAPS_API_KEY_ANDROID`); a build without one draws [TripMapCanvas].
 */
@Composable
internal actual fun TripMap(
    scene: TripMapScene,
    onPinClick: (id: String) -> Unit,
    onMapClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val hasKey = remember(context) { context.hasMapsApiKey() }
    if (!hasKey) {
        MissingKey.warnOnce()
        TripMapCanvas(scene, onPinClick, onMapClick, contentPadding, modifier)
        return
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val edge =
            with(density) {
                scene.camera.edgePaddingPx
                    .toDp()
                    .value
            }
        val ground = mapGround().toArgb()
        // Created already framed on the pins (M4-M9): no world view at zoom 0 first, and no wait for the
        // tiles (`onMapLoaded`), which on a slow or failing network may be a long wait or never come.
        // This is the map's only framing: it is framed once, when created (picking a pin never re-frames).
        val camera =
            rememberCameraPositionState {
                position =
                    cameraToFit(scene.camera.bounds, maxWidth.value, maxHeight.value, contentPadding.toInsets(direction), edge).toPosition()
            }
        // The padding the map is created with; later changes keep the map still (below).
        val createdWith = remember { contentPadding }
        val properties = remember(scene.style) { MapProperties(mapStyleOptions = MapStyleOptions(scene.style)) }
        val settings =
            remember {
                MapUiSettings(
                    compassEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                    rotationGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                    indoorLevelPickerEnabled = false,
                )
            }
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            // The tiles' placeholder: the app's map ground, not the SDK's grey (M4-M9).
            googleMapOptionsFactory = { GoogleMapOptions().backgroundColor(ground) },
            properties = properties,
            uiSettings = settings,
            contentPadding = createdWith,
            onMapClick = { onMapClick() },
        ) {
            PadAndReveal(contentPadding, createdWith, scene.reveal, scene.motion, constraints.maxHeight)
            scene.markers.forEach { marker ->
                key(marker.id) { PinMarker(marker, scene.motion, onPinClick) }
            }
        }
    }
}

/**
 * After the map was created (framed once, at creation), in one effect: the chrome's space changing
 * (the card opening or closing, M4-M9), and the picked pin kept clear of its card. The map's padding
 * follows the chrome, so Google's logo stays clear of the card, and the camera shifts by half the
 * change: padding alone re-centres the map in the new padded area and would move everything under the
 * card; with the shift, nothing on the map moves ([keepStillShift]). Then, once the picked pin's card
 * is measured ([reveal]), a pin the card would cover is panned just clear of it ([revealShift]) over
 * [BahrMotion.Medium] (the SDK's own easing), or at once under reduce motion ([motion] false); a pin
 * already clear does not move.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
private fun PadAndReveal(
    padding: PaddingValues,
    createdWith: PaddingValues,
    reveal: PinReveal?,
    motion: Boolean,
    heightPx: Int,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val applied = remember { mutableStateOf(createdWith.toPx(density, direction)) }
    val revealed = remember { mutableStateOf<PinReveal?>(null) }
    MapEffect(padding, reveal) { map ->
        val now = padding.toPx(density, direction)
        val before = applied.value
        if (now != before) {
            map.setPadding(now.left, now.top, now.right, now.bottom)
            applied.value = now
            val (dx, dy) = keepStillShift(before.toInsets(), now.toInsets())
            map.moveCamera(CameraUpdateFactory.scrollBy(dx, dy))
        }
        if (reveal != null && shouldReveal(reveal, revealed.value)) {
            val anchor = map.projection.toScreenLocation(reveal.point.toLatLng())
            val shift = revealShift(anchor.y.toFloat(), heightPx.toFloat(), reveal.cardHeightPx.toFloat(), reveal.clearancePx)
            if (shift > 0f) {
                val pan = CameraUpdateFactory.scrollBy(0f, shift)
                if (motion) map.animateCamera(pan, BahrMotion.Medium, null) else map.moveCamera(pan)
            }
        }
        revealed.value = reveal
    }
}

/** A marker; the selected pin springs to its grown size ([animatedPinScale]) in cached steps. */
@Composable
@GoogleMapComposable
private fun PinMarker(
    marker: MapMarker,
    motion: Boolean,
    onPinClick: (id: String) -> Unit,
) {
    // A Google marker has no scale: each step of the spring is the grown picture drawn smaller (or a
    // touch larger at the overshoot), in steps of [SCALE_STEP]. The steps are kept per picture, so the
    // overshoot's way back reuses the ones drawn on the way up: about 15 small pictures per selection.
    val step = (animatedPinScale(marker, motion) / SCALE_STEP).roundToInt()
    val steps = remember(marker.image) { mutableMapOf<Int, BitmapDescriptor>() }
    val icon =
        steps.getOrPut(step) {
            BitmapDescriptorFactory.fromBitmap(
                marker.image.bitmap
                    .asAndroidBitmap()
                    .scaled(step * SCALE_STEP),
            )
        }
    Marker(
        state = rememberUpdatedMarkerState(position = marker.point.toLatLng()),
        icon = icon,
        anchor = Offset(marker.image.anchorX, marker.image.anchorY),
        zIndex = marker.zIndex,
        contentDescription = marker.description,
        onClick = {
            if (marker.clickable) onPinClick(marker.id)
            // Consumed: no info window, and the camera stays where the user put it.
            true
        },
    )
}

private fun Bitmap.scaled(scale: Float): Bitmap {
    if (scale == 1f) return this
    val w = (width * scale).roundToInt().coerceAtLeast(1)
    val h = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, w, h, true)
}

/** Padding in whole pixels, physical sides (the map has no reading direction). */
private data class PaddingPx(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun toInsets() = MapInsets(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

private fun PaddingValues.toPx(
    density: Density,
    direction: LayoutDirection,
) = with(density) {
    PaddingPx(
        left = calculateLeftPadding(direction).roundToPx(),
        top = calculateTopPadding().roundToPx(),
        right = calculateRightPadding(direction).roundToPx(),
        bottom = calculateBottomPadding().roundToPx(),
    )
}

private fun PaddingValues.toInsets(direction: LayoutDirection) =
    MapInsets(
        left = calculateLeftPadding(direction).value,
        top = calculateTopPadding().value,
        right = calculateRightPadding(direction).value,
        bottom = calculateBottomPadding().value,
    )

private fun CameraPose.toPosition() = CameraPosition.fromLatLngZoom(target.toLatLng(), zoom)

private fun GeoPoint.toLatLng() = LatLng(lat, lng)

/**
 * The selected pin's spring is drawn in steps of 1% of its grown size: fine enough to keep its small
 * overshoot (under 2%).
 */
private const val SCALE_STEP = .01f

private const val MAPS_KEY_META_DATA = "com.google.android.geo.API_KEY"

/** Whether the manifest carries a Maps key. Reads presence only; the key itself is never logged. */
private fun Context.hasMapsApiKey(): Boolean {
    val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }
    return !info.metaData?.getString(MAPS_KEY_META_DATA).isNullOrBlank()
}

/** One warning per process, not one per recomposition. */
private object MissingKey {
    private var warned = false

    fun warnOnce() {
        if (warned) return
        warned = true
        Logger.withTag("TripMap").w {
            "No Google Maps API key in this build (MAPS_API_KEY_ANDROID in local.properties): pins drawn on a plain ground."
        }
    }
}
