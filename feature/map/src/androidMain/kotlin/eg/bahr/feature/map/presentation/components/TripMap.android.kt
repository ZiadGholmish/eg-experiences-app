package eg.bahr.feature.map.presentation.components

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.feature.map.presentation.GeoPoint

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

    val camera = rememberCameraPositionState()
    var loaded by remember { mutableStateOf(false) }
    // The first framing jumps; later ones (a toggle, a new drive) glide unless reduce motion is on.
    var framedOnce by remember { mutableStateOf(false) }
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
    val target by rememberUpdatedState(scene.camera)

    // A bounds update needs the map laid out, which onMapLoaded promises.
    LaunchedEffect(scene.camera.id, loaded) {
        if (!loaded) return@LaunchedEffect
        val fitted = CameraUpdateFactory.newLatLngBounds(target.bounds.toLatLngBounds(), target.edgePaddingPx)
        // A bounds update throws when the padding leaves no room (a small or landscape screen with
        // the card open, a large font): then centre on the box at a country-wide zoom instead.
        val centred = CameraUpdateFactory.newLatLngZoom(target.bounds.center.toLatLng(), FALLBACK_ZOOM)
        try {
            if (framedOnce && target.animate) camera.animate(fitted, BahrMotion.Long) else camera.move(fitted)
        } catch (tooSmall: IllegalStateException) {
            camera.move(centred)
        }
        framedOnce = true
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = properties,
        uiSettings = settings,
        contentPadding = contentPadding,
        onMapLoaded = { loaded = true },
        onMapClick = { onMapClick() },
    ) {
        scene.markers.forEach { marker ->
            key(marker.id) {
                val icon = remember(marker.image) { BitmapDescriptorFactory.fromBitmap(marker.image.bitmap.asAndroidBitmap()) }
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
        }
        scene.route?.let { route ->
            Polyline(
                points = listOf(route.from.toLatLng(), route.to.toLatLng()),
                color = route.color,
                width = route.widthPx,
                pattern = listOf(Dash(route.dashPx), Gap(route.gapPx)),
            )
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(lat, lng)

private fun GeoBounds.toLatLngBounds() = LatLngBounds(LatLng(south, west), LatLng(north, east))

/** Google's zoom 6: most of Egypt's Nile valley and Delta in view. */
private const val FALLBACK_ZOOM = 6f

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
