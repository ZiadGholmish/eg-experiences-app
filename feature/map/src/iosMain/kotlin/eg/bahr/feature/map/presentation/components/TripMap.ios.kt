package eg.bahr.feature.map.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import co.touchlab.kermit.Logger
import eg.bahr.feature.map.di.TripMapNativeEvents
import eg.bahr.feature.map.di.TripMapNativeMarker
import eg.bahr.feature.map.di.TripMapNativeRoute
import eg.bahr.feature.map.di.TripMapNativeViews
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.koin.compose.getKoin
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIView

/**
 * iOS: the Google Maps SDK for iOS through the Swift bridge ([TripMapNativeViews]), in a UIKit view
 * under the Compose chrome. The app registers the bridge only when it has an API key
 * (`MAPS_API_KEY_IOS`); without one this draws [TripMapCanvas].
 *
 * Markers are re-sent when their pictures change (a selection, the drive toggle), which for a
 * catalogue-sized map is a handful of small images.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun TripMap(
    scene: TripMapScene,
    onPinClick: (id: String) -> Unit,
    onMapClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val koin = getKoin()
    val views = remember(koin) { koin.getOrNull<TripMapNativeViews>() }
    if (views == null) {
        MissingKey.warnOnce()
        TripMapCanvas(scene, onPinClick, onMapClick, contentPadding, modifier)
        return
    }

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val pinClick by rememberUpdatedState(onPinClick)
    val mapClick by rememberUpdatedState(onMapClick)
    val events =
        remember {
            object : TripMapNativeEvents {
                override fun onMarkerTap(id: String) = pinClick(id)

                override fun onMapTap() = mapClick()
            }
        }
    var map by remember { mutableStateOf<UIView?>(null) }
    var framedOnce by remember { mutableStateOf(false) }
    val icons = remember { IconCache(density.density.toDouble()) }

    UIKitView(
        factory = { views.createMap(scene.style, events).also { map = it } },
        modifier = modifier,
        properties =
            UIKitInteropProperties(
                // The map owns its gestures at once: there is nothing around it to scroll.
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true,
            ),
    )

    val view = map ?: return
    LaunchedEffect(view, contentPadding, direction) {
        views.setPadding(
            view,
            top = contentPadding.calculateTopPadding().value.toDouble(),
            left = contentPadding.calculateLeftPadding(direction).value.toDouble(),
            bottom = contentPadding.calculateBottomPadding().value.toDouble(),
            right = contentPadding.calculateRightPadding(direction).value.toDouble(),
        )
    }
    LaunchedEffect(view, scene.markers) {
        icons.keepOnly(scene.markers.map { it.image })
        views.setMarkers(view, scene.markers.mapNotNull { it.toNative(icons) })
    }
    LaunchedEffect(view, scene.route) {
        views.setRoute(view, scene.route?.toNative(density.density.toDouble()))
    }
    LaunchedEffect(view, scene.camera.id) {
        val camera = scene.camera
        val b = camera.bounds
        views.fitBounds(
            view,
            south = b.south,
            west = b.west,
            north = b.north,
            east = b.east,
            edgePadding = camera.edgePaddingPx / density.density.toDouble(),
            animated = framedOnce && camera.animate,
        )
        framedOnce = true
    }
}

private fun MapMarker.toNative(icons: IconCache): TripMapNativeMarker? {
    val icon = icons[image] ?: return null
    return TripMapNativeMarker(
        id = id,
        lat = point.lat,
        lng = point.lng,
        icon = icon,
        anchorX = image.anchorX.toDouble(),
        anchorY = image.anchorY.toDouble(),
        zIndex = zIndex.toInt(),
        tappable = clickable,
        accessibilityLabel = description,
    )
}

/** Sizes are drawn in pixels; UIKit wants points, so they are divided by the screen [scale]. */
private fun MapRoute.toNative(scale: Double) =
    TripMapNativeRoute(
        fromLat = from.lat,
        fromLng = from.lng,
        toLat = to.lat,
        toLng = to.lng,
        color = color.toUIColor(),
        width = widthPx / scale,
        dash = dashPx / scale,
        gap = gapPx / scale,
    )

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

/**
 * The marker pictures as `UIImage`s at the screen's [scale], so a pin is as many points wide as the
 * Compose pin is dp. Kept by picture, so a re-sent marker list encodes only what changed (the common
 * painter reuses unchanged pictures), and trimmed to the pictures on screen each time.
 */
private class IconCache(
    private val scale: Double,
) {
    private val images = mutableMapOf<MarkerImage, UIImage>()

    operator fun get(image: MarkerImage): UIImage? = images[image] ?: image.bitmap.toUIImage(scale)?.also { images[image] = it }

    /** Forgets every picture not in [current]: the cache never holds more than the map shows. */
    fun keepOnly(current: Collection<MarkerImage>) {
        val keep = current.toSet()
        images.keys.retainAll(keep)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ImageBitmap.toUIImage(scale: Double): UIImage? {
    val png = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
    val data = png.usePinned { NSData.dataWithBytes(it.addressOf(0), png.size.toULong()) }
    return UIImage.imageWithData(data, scale)
}

/** One warning per process, not one per recomposition. */
private object MissingKey {
    private var warned = false

    fun warnOnce() {
        if (warned) return
        warned = true
        Logger.withTag("TripMap").w {
            "No Google Maps bridge (no MAPS_API_KEY_IOS in this build): pins drawn on a plain ground."
        }
    }
}
