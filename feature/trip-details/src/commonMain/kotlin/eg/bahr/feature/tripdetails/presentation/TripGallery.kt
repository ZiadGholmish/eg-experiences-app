package eg.bahr.feature.tripdetails.presentation

import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.presentation.media.ViewerMedia
import eg.bahr.feature.tripdetails.model.TripDetailDto
import eg.bahr.feature.tripdetails.model.TripMediaDto

/**
 * The slider's items, which the full-screen viewer pages through too (MV-M3): the trip's `media` (photos and videos, in
 * the server's order, M5-B4), else its photo `gallery` (a server before M5-B4), else the hero or card image alone. A
 * `media` item of a kind this build does not know is skipped; if that leaves nothing, the photo gallery is used.
 */
internal fun TripDetailDto.galleryMedia(): List<ViewerMedia> {
    val mixed = media?.mapNotNull(TripMediaDto::toViewerMedia).orEmpty()
    if (mixed.isNotEmpty()) return mixed
    return gallery.ifEmpty { listOfNotNull(heroImage ?: cardImage) }.map { ViewerMedia.Photo(it) }
}

/** One `TripMedia` as a viewer item, or null for a kind this build does not know. */
internal fun TripMediaDto.toViewerMedia(): ViewerMedia? =
    when (kind) {
        KIND_PHOTO -> ViewerMedia.Photo(ImageDto(url = url, width = width, height = height, alt = alt, lqip = lqip, variants = variants))
        KIND_VIDEO ->
            ViewerMedia.Video(
                url = url,
                width = width,
                height = height,
                alt = alt,
                durationSeconds = durationSeconds,
                poster = poster,
                lqip = lqip ?: poster?.lqip,
            )
        else -> null
    }

private const val KIND_PHOTO = "photo"
private const val KIND_VIDEO = "video"
