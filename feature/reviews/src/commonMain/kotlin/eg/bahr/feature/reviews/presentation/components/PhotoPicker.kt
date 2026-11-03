package eg.bahr.feature.reviews.presentation.components

import androidx.compose.runtime.Composable

/**
 * The system photo picker (M3-M4): Android's Photo Picker (`PickVisualMedia`, no storage permission), iOS's
 * `PHPickerViewController` (no library permission). Neither needs a library: Google publishes no multiplatform picker.
 */
internal fun interface PhotoPicker {
    /** Opens the picker for at most [max] photos (at least 1). */
    fun pick(max: Int)
}

/**
 * The platform's picker. Each picked photo is decoded at a bounded size, turned upright (its EXIF orientation applied
 * to the pixels) and re-encoded as a JPEG of at most [PhotoCompression.MAX_EDGE] pixels on its long edge, off the main
 * thread, before [onPicked] gets the JPEGs. The server takes only JPEG and PNG, so HEIC and WebP are converted here.
 * [onPicked]'s `failed` counts the photos that could not be read.
 */
@Composable
internal expect fun rememberPhotoPicker(onPicked: (jpegs: List<ByteArray>, failed: Int) -> Unit): PhotoPicker

/** How a picked photo is compressed before upload (the server allows 10 MB each; this keeps one near 0.3–0.6 MB). */
internal object PhotoCompression {
    /** Pixels on the long edge: sharp on a phone's full-screen view, a fraction of a camera original. */
    const val MAX_EDGE = 1600

    /** JPEG quality, 0–100. */
    const val JPEG_QUALITY = 82
}
