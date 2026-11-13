package eg.bahr.core.catalog.presentation.media

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens and closes the one full-screen viewer (MV-M3). [MediaViewerHost] owns it and draws the viewer over the screens
 * under it; anything below the host opens a gallery through [LocalMediaViewer] (the trip page's slider, a review's photos,
 * a host's avatar), so no feature has to know another to show a photo full screen.
 *
 * Closing is two steps so the page can fly back to its thumbnail: [close] hides the viewer ([shown] false; the thumbnail
 * shows again and the page flies to it), and the host calls [onClosed] when that has finished, which drops the [session].
 */
@Stable
class MediaViewerController {
    /** The open gallery, kept until the close has finished. */
    var session: MediaViewerState? by mutableStateOf(null)
        private set

    /** Whether the viewer is open (false while it closes). */
    var shown: Boolean by mutableStateOf(false)
        private set

    /**
     * The one item that flies between its thumbnail and the viewer: the item tapped, on the way in; on the way out the
     * same item if the reader closed on it, else none (the viewer fades and every thumbnail stays where it is). Only
     * this item's thumbnail and page take part, so paging never hides a thumbnail or a page.
     */
    var flightKey: String? by mutableStateOf(null)
        private set

    private var onDismiss: ((page: Int) -> Unit)? = null

    /**
     * Opens [items] on [index]. [onDismiss] hears the page the reader closed on, before the close flight, so a slider
     * can turn to it and the page lands on its own thumbnail. An empty gallery opens nothing.
     */
    fun open(
        items: List<ViewerMedia>,
        index: Int,
        onDismiss: ((page: Int) -> Unit)? = null,
    ) {
        if (items.isEmpty()) return
        val opened = MediaViewerState(items, index)
        session = opened
        flightKey = opened.current?.key
        this.onDismiss = onDismiss
        shown = true
    }

    fun close() {
        val open = session ?: return
        if (!shown) return
        onDismiss?.invoke(open.page)
        onDismiss = null
        if (open.current?.key != flightKey) flightKey = null
        shown = false
    }

    /** The close has finished: the viewer leaves the composition. */
    fun onClosed() {
        if (shown) return
        session = null
        flightKey = null
    }

    /** Whether the thumbnail of [key] is hidden: it is the item in flight, and it is "in" the open viewer. */
    fun hidesThumbnail(key: String): Boolean = shown && key == flightKey
}

/** The viewer of the screen being drawn; null outside a [MediaViewerHost] (tests, previews), where nothing opens. */
val LocalMediaViewer = staticCompositionLocalOf<MediaViewerController?> { null }
