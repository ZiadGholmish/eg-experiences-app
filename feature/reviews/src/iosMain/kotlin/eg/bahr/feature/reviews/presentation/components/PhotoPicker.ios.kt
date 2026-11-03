package eg.bahr.feature.reviews.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

@Composable
internal actual fun rememberPhotoPicker(onPicked: (jpegs: List<ByteArray>, failed: Int) -> Unit): PhotoPicker {
    val host = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    return remember(host, scope) { IosPhotoPicker(host, scope) { jpegs, failed -> latest(jpegs, failed) } }
}

/**
 * `PHPickerViewController`, presented over the Compose root's view controller. The picker holds its delegate weakly, so
 * this keeps the one it set ([delegate]) for as long as the picker may call it.
 */
private class IosPhotoPicker(
    private val host: UIViewController,
    private val scope: CoroutineScope,
    private val onPicked: (List<ByteArray>, Int) -> Unit,
) : PhotoPicker {
    private var delegate: PickerDelegate? = null

    override fun pick(max: Int) {
        val configuration =
            PHPickerConfiguration().apply {
                selectionLimit = max.coerceAtLeast(1).toLong()
                filter = PHPickerFilter.imagesFilter
            }
        val picker = PHPickerViewController(configuration = configuration)
        val pickerDelegate =
            PickerDelegate { results ->
                delegate = null
                if (results.isEmpty()) return@PickerDelegate
                scope.launch {
                    val jpegs = withContext(Dispatchers.IO) { results.map { runCatching { jpeg(it) }.getOrNull() } }
                    onPicked(jpegs.filterNotNull(), jpegs.count { it == null })
                }
            }
        delegate = pickerDelegate
        picker.delegate = pickerDelegate
        host.presentViewController(picker, animated = true, completion = null)
    }
}

private class PickerDelegate(
    private val onResults: (List<PHPickerResult>) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResults(didFinishPicking.filterIsInstance<PHPickerResult>())
    }
}

/**
 * One picked photo as an upright JPEG at most [PhotoCompression.MAX_EDGE] pixels on its long edge. Drawing the UIImage
 * through a renderer applies its orientation to the pixels (re-encoding drops the tag); HEIC comes out as JPEG.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun jpeg(result: PHPickerResult): ByteArray? {
    val data = loadImageData(result) ?: return null
    val image = UIImage.imageWithData(data) ?: return null
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = min(1.0, PhotoCompression.MAX_EDGE / max(width, height))
    val targetWidth = width * scale
    val targetHeight = height * scale
    // Points are pixels here: the renderer's default would multiply by the screen's scale.
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
    val upright = renderer.imageWithActions { image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight)) }
    val encoded = UIImageJPEGRepresentation(upright, PhotoCompression.JPEG_QUALITY / PERCENT) ?: return null
    return encoded.toByteArray()
}

/** The photo's file data ("public.image": JPEG, HEIC, PNG, …); null when it cannot be loaded (e.g. an iCloud failure). */
private suspend fun loadImageData(result: PHPickerResult): NSData? =
    suspendCancellableCoroutine { continuation ->
        val progress =
            result.itemProvider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, _ ->
                if (continuation.isActive) continuation.resume(data)
            }
        continuation.invokeOnCancellation { progress.cancel() }
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

/** UTType.image's identifier. */
private const val IMAGE_TYPE = "public.image"

/** JPEG quality is 0–100 here, 0–1 for UIKit. */
private const val PERCENT = 100.0
