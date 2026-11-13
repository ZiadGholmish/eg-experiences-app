package eg.bahr.core.catalog.presentation.media

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * One open full-screen viewer (MV-M3): the gallery's [items] in server order, the page the reader is on and the zoom of
 * that page. Plain Kotlin over snapshot state, so its rules are unit-tested without a screen:
 *
 * - it opens on the item tapped ([startIndex], held to the gallery);
 * - only the page on screen can be zoomed, and settling on another page starts it unzoomed;
 * - while zoomed, a one-finger drag pans the photo, so the pager does not page and a drag down does not dismiss
 *   ([pagingEnabled], [dismissEnabled]); pinching back to 1× gives both back;
 * - the pan is held so the photo's edge never comes inside the screen's.
 */
@Stable
class MediaViewerState(
    val items: List<ViewerMedia>,
    startIndex: Int = 0,
    initialZoom: Float = MIN_ZOOM,
) {
    /** The page it opened on. */
    val startPage: Int = if (items.isEmpty()) 0 else startIndex.coerceIn(0, items.lastIndex)

    /** The page the pager has settled on. */
    var page: Int by mutableIntStateOf(startPage)
        private set

    /** The settled page's zoom: 1 (fits the screen) to [MAX_ZOOM]. */
    var zoom: Float by mutableFloatStateOf(initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set

    /** The settled page's pan, in pixels from the centre, applied after [zoom]. */
    var pan: Offset by mutableStateOf(Offset.Zero)
        private set

    val current: ViewerMedia? get() = items.getOrNull(page)

    val zoomed: Boolean get() = zoom > MIN_ZOOM + ZOOM_EPSILON

    /** Whether a sideways swipe pages: not while zoomed, when the same swipe pans the photo. */
    val pagingEnabled: Boolean get() = !zoomed

    /** Whether a drag down closes the viewer: not while zoomed, when the same drag pans the photo. */
    val dismissEnabled: Boolean get() = !zoomed

    /** "3 / 8" as numbers: the page on screen, counted from 1, and how many there are. */
    val position: Int get() = page + 1
    val count: Int get() = items.size

    /** The pager settled on [settled]: a new page starts unzoomed. */
    fun onPageSettled(settled: Int) {
        if (settled == page || settled !in items.indices) return
        page = settled
        resetZoom()
    }

    /**
     * A pinch or a zoomed pan on a [viewport]-sized page: [zoomChange] multiplies the zoom (held to 1–[MAX_ZOOM]) about
     * [centroid] (the point between the fingers stays under them; the page's centre when absent) and [panChange] moves
     * the photo, held inside the room the zoom made. At 1× there is no room, so nothing pans.
     */
    fun transform(
        zoomChange: Float,
        panChange: Offset,
        viewport: Size,
        centroid: Offset? = null,
    ) {
        val next = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = next / zoom
        val centre = Offset(viewport.width / 2, viewport.height / 2)
        // Scaling about the centre by `applied` moves the point under the fingers; the pan takes that back.
        val fromCentre = (centroid ?: centre) - centre
        zoom = next
        pan = clampPan(fromCentre * (1 - applied) + pan * applied + panChange, next, viewport)
    }

    /**
     * A double tap at [tap] on a [viewport]-sized page: zoomed in, back to fit; at fit, to [DOUBLE_TAP_ZOOM] with the
     * tapped point kept under the finger (as far as the edges allow).
     */
    fun toggleZoom(
        tap: Offset,
        viewport: Size,
    ) {
        if (zoomed) {
            resetZoom()
            return
        }
        zoom = DOUBLE_TAP_ZOOM
        val centre = Offset(viewport.width / 2, viewport.height / 2)
        // Scaling about the centre moves the tapped point by (tap - centre) * (zoom - 1); panning back by that keeps it put.
        pan = clampPan((centre - tap) * (DOUBLE_TAP_ZOOM - MIN_ZOOM), DOUBLE_TAP_ZOOM, viewport)
    }

    fun resetZoom() {
        zoom = MIN_ZOOM
        pan = Offset.Zero
    }

    private fun clampPan(
        wanted: Offset,
        zoom: Float,
        viewport: Size,
    ): Offset {
        val maxX = viewport.width * (zoom - MIN_ZOOM) / 2
        val maxY = viewport.height * (zoom - MIN_ZOOM) / 2
        return Offset(wanted.x.coerceIn(-maxX, maxX), wanted.y.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MIN_ZOOM = 1f

        /** Far enough to read a sign in a photo; past it the original's pixels show. */
        const val MAX_ZOOM = 4f

        /** A double tap's zoom: about what a pinch reaches in one go. */
        const val DOUBLE_TAP_ZOOM = 2.5f

        /** A pinch released a hair above 1× counts as not zoomed, so paging comes back. */
        private const val ZOOM_EPSILON = 0.01f
    }
}
