package eg.bahr.feature.reviews.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import eg.bahr.feature.reviews.model.ReviewLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

@Composable
internal actual fun rememberPhotoPicker(onPicked: (jpegs: List<ByteArray>, failed: Int) -> Unit): PhotoPicker {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    val handle: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            // Read while the picker's grant on the URIs lasts, off the main thread: a camera photo is tens of MB decoded.
            scope.launch {
                val jpegs = withContext(Dispatchers.IO) { uris.map { runCatching { compress(context, it) }.getOrNull() } }
                latest(jpegs.filterNotNull(), jpegs.count { it == null })
            }
        }
    }
    // PickMultipleVisualMedia requires a limit above 1, so a single free slot uses the one-photo picker. The view model
    // keeps no more than the slots left either way.
    val many = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ReviewLimits.MAX_PHOTOS), handle)
    val one = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> handle(listOfNotNull(uri)) }
    return remember(many, one) {
        PhotoPicker { max ->
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (max > 1) many.launch(request) else one.launch(request)
        }
    }
}

/** One picked photo as an upright JPEG of at most [PhotoCompression.MAX_EDGE] pixels on its long edge. */
private fun compress(
    context: Context,
    uri: Uri,
): ByteArray {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeScaled(context, uri) else decodeScaledLegacy(context, uri)
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoCompression.JPEG_QUALITY, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

/** API 28+: ImageDecoder decodes straight to the target size and applies the EXIF orientation itself (HEIC too). */
@RequiresApi(Build.VERSION_CODES.P)
private fun decodeScaled(
    context: Context,
    uri: Uri,
): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val scale = scaleFor(info.size.width, info.size.height)
        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        // A software bitmap: Bitmap.compress cannot read a hardware one.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

/**
 * API 24–27: BitmapFactory samples down by a power of two while decoding (so a large original never sits in memory
 * whole), then the EXIF orientation is applied to the pixels, since re-encoding drops the tag and the photo would
 * otherwise arrive sideways; a last exact scale brings it to the bound.
 */
private fun decodeScaledLegacy(
    context: Context,
    uri: Uri,
): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PhotoCompression.MAX_EDGE) sample *= 2
    val decoded =
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("not an image")
    val orientation =
        resolver
            .openInputStream(
                uri,
            )?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(QUARTER_TURN)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(2 * QUARTER_TURN)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(3 * QUARTER_TURN)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    }
    val scale = scaleFor(decoded.width, decoded.height)
    if (scale < 1f) matrix.postScale(scale, scale)
    if (matrix.isIdentity) return decoded
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (upright !== decoded) decoded.recycle()
    return upright
}

/** The factor that brings a [width] × [height] photo's long edge down to the bound; 1 or more leaves it as it is. */
private fun scaleFor(
    width: Int,
    height: Int,
): Float = PhotoCompression.MAX_EDGE.toFloat() / max(width, height)

private const val QUARTER_TURN = 90f
