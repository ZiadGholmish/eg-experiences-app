package eg.bahr.core.catalog.presentation.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import eg.bahr.core.catalog.model.ImageDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The full-screen viewer's rules (MV-M3): where it opens, paging, and zoom taking the drag from the pager. */
class MediaViewerStateTest {
    private val viewport = Size(VIEWPORT_W, VIEWPORT_H)

    @Test
    fun `opens on the item tapped counted from 1`() {
        val state = MediaViewerState(gallery(8), startIndex = 2)

        assertEquals(2, state.page)
        assertEquals(3, state.position)
        assertEquals(8, state.count)
        assertEquals("photo-2", state.current?.key)
    }

    @Test
    fun `a start index outside the gallery is held to it`() {
        assertEquals(0, MediaViewerState(gallery(3), startIndex = -1).startPage)
        assertEquals(2, MediaViewerState(gallery(3), startIndex = 9).startPage)
        assertNull(MediaViewerState(emptyList(), startIndex = 4).current)
    }

    @Test
    fun `paging moves the counter and a page outside the gallery is ignored`() {
        val state = MediaViewerState(gallery(3), startIndex = 0)

        state.onPageSettled(1)
        assertEquals(2, state.position)
        state.onPageSettled(7)
        assertEquals(2, state.position)
    }

    @Test
    fun `not zoomed a sideways swipe pages and a drag down dismisses`() {
        val state = MediaViewerState(gallery(3))

        assertFalse(state.zoomed)
        assertTrue(state.pagingEnabled)
        assertTrue(state.dismissEnabled)
    }

    @Test
    fun `zoomed the drag pans the photo instead of paging or dismissing`() {
        val state = MediaViewerState(gallery(3))

        state.transform(zoomChange = 2f, panChange = Offset.Zero, viewport = viewport)

        assertTrue(state.zoomed)
        assertFalse(state.pagingEnabled)
        assertFalse(state.dismissEnabled)
    }

    @Test
    fun `pinching back to fit gives paging back`() {
        val state = MediaViewerState(gallery(3), initialZoom = 2f)

        state.transform(zoomChange = 0.5f, panChange = Offset.Zero, viewport = viewport)

        assertFalse(state.zoomed)
        assertTrue(state.pagingEnabled)
    }

    @Test
    fun `settling on another page starts it unzoomed`() {
        val state = MediaViewerState(gallery(3), initialZoom = 3f)

        state.onPageSettled(1)

        assertEquals(MediaViewerState.MIN_ZOOM, state.zoom)
        assertEquals(Offset.Zero, state.pan)
        assertTrue(state.pagingEnabled)
    }

    @Test
    fun `zoom is held between fit and the maximum`() {
        val state = MediaViewerState(gallery(1))

        state.transform(zoomChange = 100f, panChange = Offset.Zero, viewport = viewport)
        assertEquals(MediaViewerState.MAX_ZOOM, state.zoom)
        state.transform(zoomChange = 0.01f, panChange = Offset.Zero, viewport = viewport)
        assertEquals(MediaViewerState.MIN_ZOOM, state.zoom)
    }

    @Test
    fun `at fit nothing pans and zoomed the pan stops at the photo's edge`() {
        val state = MediaViewerState(gallery(1))

        state.transform(zoomChange = 1f, panChange = Offset(300f, 300f), viewport = viewport)
        assertEquals(Offset.Zero, state.pan)

        state.transform(zoomChange = 2f, panChange = Offset(10_000f, -10_000f), viewport = viewport)
        // At 2× the photo is twice the screen: half a screen of room each way.
        assertEquals(Offset(VIEWPORT_W / 2, -VIEWPORT_H / 2), state.pan)
    }

    @Test
    fun `a double tap zooms in on the point tapped and a second one fits again`() {
        val state = MediaViewerState(gallery(1))
        val centre = Offset(VIEWPORT_W / 2, VIEWPORT_H / 2)

        state.toggleZoom(centre, viewport)
        assertEquals(MediaViewerState.DOUBLE_TAP_ZOOM, state.zoom)
        assertEquals(Offset.Zero, state.pan, "the centre stays put")

        state.toggleZoom(centre, viewport)
        assertFalse(state.zoomed)

        // Tapped left of centre: the photo moves right so that point stays under the finger.
        state.toggleZoom(Offset(centre.x - 100f, centre.y), viewport)
        assertEquals(100f * (MediaViewerState.DOUBLE_TAP_ZOOM - 1), state.pan.x)
    }

    @Test
    fun `a pinch keeps the point between the fingers under them`() {
        val state = MediaViewerState(gallery(1))
        val centre = Offset(VIEWPORT_W / 2, VIEWPORT_H / 2)
        val fingers = Offset(centre.x + 100f, centre.y)

        state.transform(zoomChange = 2f, panChange = Offset.Zero, viewport = viewport, centroid = fingers)

        // The content point under the fingers was 100 px right of centre; at 2× it would be 200 px: the pan takes 100 back.
        assertEquals(-100f, state.pan.x)
    }

    private fun gallery(size: Int): List<ViewerMedia> =
        List(size) { ViewerMedia.Photo(ImageDto(url = "photo-$it", width = 900, height = 560)) }

    private companion object {
        const val VIEWPORT_W = 1080f
        const val VIEWPORT_H = 2400f
    }
}
