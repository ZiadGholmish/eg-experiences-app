package eg.bahr.feature.map.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.feature.map.di.TripMapNativeEvents
import eg.bahr.feature.map.di.TripMapNativeMarker
import eg.bahr.feature.map.di.TripMapNativeStart
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
 * Markers are re-sent when their pictures change (a selection), which for a
 * catalogue-sized map is a handful of small images; the bridge updates them in place by id. The
 * selected pin's spring runs natively there (a UIKit spring on the marker's view, M4-M9), on the
 * shared [BahrMotion] numbers: Kotlin cannot animate a GMSMarker frame by frame without re-encoding
 * its picture each frame. The map is created already framed and on the map's placeholder ground, and
 * keeps still when the chrome's padding changes under an open card.
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
    val icons = remember { IconCache(density.density.toDouble()) }
    val placeholder = mapGround().toUIColor()

    val applied = remember { Applied() }
    BoxWithConstraints(modifier) {
        // After a successful composition, not during one that may be discarded (Compose audit L10).
        val height = maxHeight.value
        SideEffect(height) { applied.height = height }
        // Created already framed on the pins (M4-M9), in points (the dp of this layout): no world view first.
        val start =
            remember {
                val camera = scene.camera
                val insets =
                    MapInsets(
                        left = contentPadding.calculateLeftPadding(direction).value,
                        top = contentPadding.calculateTopPadding().value,
                        right = contentPadding.calculateRightPadding(direction).value,
                        bottom = contentPadding.calculateBottomPadding().value,
                    )
                val pose = cameraToFit(camera.bounds, maxWidth.value, maxHeight.value, insets, camera.edgePaddingPx / density.density)
                TripMapNativeStart(
                    lat = pose.target.lat,
                    lng = pose.target.lng,
                    zoom = pose.zoom.toDouble(),
                    placeholder = placeholder,
                    springStiffness = BahrMotion.PinSpringStiffness.toDouble(),
                    springDampingRatio = BahrMotion.PinSpringDampingRatio.toDouble(),
                    easeX1 = BahrMotion.StandardControlPoints[0].toDouble(),
                    easeY1 = BahrMotion.StandardControlPoints[1].toDouble(),
                    easeX2 = BahrMotion.StandardControlPoints[2].toDouble(),
                    easeY2 = BahrMotion.StandardControlPoints[3].toDouble(),
                )
            }
        UIKitView(
            factory = { views.createMap(scene.style, events, start).also { map = it } },
            modifier = Modifier.fillMaxSize(),
            properties =
                UIKitInteropProperties(
                    // The map owns its gestures at once: there is nothing around it to scroll.
                    interactionMode = UIKitInteropInteractionMode.NonCooperative,
                    isNativeAccessibilityEnabled = true,
                ),
        )
    }

    val view = map ?: return
    // Padding: the keep-still shift is worked out here ([keepStillShift], the same as Android's) and
    // the bridge applies it once the map is framed (M4-M9 review #10).
    LaunchedEffect(view, contentPadding, direction) {
        val now =
            MapInsets(
                left = contentPadding.calculateLeftPadding(direction).value,
                top = contentPadding.calculateTopPadding().value,
                right = contentPadding.calculateRightPadding(direction).value,
                bottom = contentPadding.calculateBottomPadding().value,
            )
        val (dx, dy) = applied.padding?.let { keepStillShift(it, now) } ?: (0f to 0f)
        applied.padding = now
        views.setPadding(
            view,
            top = now.top.toDouble(),
            left = now.left.toDouble(),
            bottom = now.bottom.toDouble(),
            right = now.right.toDouble(),
            shiftX = dx.toDouble(),
            shiftY = dy.toDouble(),
        )
    }
    LaunchedEffect(view, scene.markers, scene.motion) {
        icons.keepOnly(scene.markers.map { it.image })
        views.setMarkers(view, scene.markers.mapNotNull { it.toNative(icons, scene.motion) })
    }
    // The map's one framing, exact, once it has a size (the bridge waits for its first layout). It is
    // created already standing there; picking a pin never re-frames it.
    LaunchedEffect(view) {
        val camera = scene.camera
        val b = camera.bounds
        views.fitBounds(
            view,
            south = b.south,
            west = b.west,
            north = b.north,
            east = b.east,
            edgePadding = camera.edgePaddingPx / density.density.toDouble(),
        )
    }
    // After the padding and the framing (effects run in this order): a picked pin its card would cover
    // is panned just clear of it; a request already handled is not panned again ([shouldReveal]).
    LaunchedEffect(view, scene.reveal) {
        val reveal = scene.reveal
        if (reveal != null && shouldReveal(reveal, applied.reveal)) {
            val y = views.pointY(view, reveal.point.lat, reveal.point.lng)
            if (!y.isNaN()) {
                val scale = density.density
                val shift = revealShift(y.toFloat(), applied.height, reveal.cardHeightPx / scale, reveal.clearancePx / scale)
                if (shift > 0f) views.pan(view, shift.toDouble(), if (scene.motion) BahrMotion.Medium / MILLIS_PER_SECOND else 0.0)
            }
        }
        applied.reveal = reveal
    }
}

/**
 * What this map last handed the bridge, kept across effects and not state (nothing redraws on it):
 * the padding (for the keep-still shift), the picked pin's request, and the map's height in points.
 */
private class Applied {
    var padding: MapInsets? = null
    var reveal: PinReveal? = null
    var height: Float = 0f
}

private const val MILLIS_PER_SECOND = 1000.0

private fun MapMarker.toNative(
    icons: IconCache,
    motion: Boolean,
): TripMapNativeMarker? {
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
        // Under reduce motion the selected pin is simply drawn grown.
        growFrom = if (motion) growFrom.toDouble() else 1.0,
    )
}

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
