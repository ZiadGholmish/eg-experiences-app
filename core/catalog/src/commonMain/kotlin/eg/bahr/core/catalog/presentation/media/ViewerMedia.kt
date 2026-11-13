package eg.bahr.core.catalog.presentation.media

import eg.bahr.core.catalog.model.ImageDto

/**
 * One item of a gallery the full-screen viewer (MV-M3) pages through: a photo, or a video (M5-B4's `TripMedia`, M5-M2).
 * Not a wire shape: each opener maps its own (the trip page's `TripMedia`, a review's photos, a host's avatar) to it, so
 * the viewer knows nothing about where its items came from.
 *
 * [key] is what a thumbnail and the viewer's page share for the open and close flight: the photo's or the video's URL.
 */
sealed interface ViewerMedia {
    val key: String

    /** Read out for the item (the server's localised alt text); null when it has none. */
    val alt: String?

    /** A photo. The viewer loads [ImageDto.url] (the original) and shows its LQIP, or the thumbnail already in memory, until then. */
    data class Photo(
        val image: ImageDto,
        override val alt: String? = image.alt,
    ) : ViewerMedia {
        override val key: String get() = image.url
    }

    /**
     * A video: an MP4 at [url], [width] × [height] as displayed, [durationSeconds] long (absent from a payload that left
     * it out), shown as its [poster] until it plays. [lqip] is the poster's.
     */
    data class Video(
        val url: String,
        val width: Int,
        val height: Int,
        override val alt: String? = null,
        val durationSeconds: Int? = null,
        val poster: ImageDto? = null,
        val lqip: String? = poster?.lqip,
    ) : ViewerMedia {
        override val key: String get() = url

        /** Width over height, for the surface's box before the first frame; square when the payload has no size. */
        val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
    }
}

/** The shared-element key of a gallery item: the thumbnail's and the viewer page's (MV-M3). */
internal fun mediaSharedKey(item: ViewerMedia): String = "media:${item.key}"
