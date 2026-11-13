package eg.bahr.core.catalog.presentation.media

import eg.bahr.core.catalog.model.ImageDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Opening and closing the one viewer (MV-M3): the session outlives the close until its animation has finished. */
class MediaViewerControllerTest {
    private val photos = List(3) { ViewerMedia.Photo(ImageDto(url = "photo-$it", width = 900, height = 560)) }

    @Test
    fun `opens a gallery on the item tapped and hides its thumbnail meanwhile`() {
        val viewer = MediaViewerController()

        viewer.open(photos, index = 1)

        assertTrue(viewer.shown)
        assertEquals(1, viewer.session?.page)
        assertTrue(viewer.hidesThumbnail("photo-1"))
        assertFalse(viewer.hidesThumbnail("photo-0"))
    }

    @Test
    fun `an empty gallery opens nothing`() {
        val viewer = MediaViewerController()

        viewer.open(emptyList(), index = 0)

        assertFalse(viewer.shown)
        assertNull(viewer.session)
    }

    @Test
    fun `closing reports the page closed on and then keeps the session until the close has finished`() {
        val viewer = MediaViewerController()
        var closedOn: Int? = null
        viewer.open(photos, index = 0) { closedOn = it }
        viewer.session?.onPageSettled(2)

        viewer.close()

        assertEquals(2, closedOn)
        assertFalse(viewer.shown)
        assertNull(viewer.flightKey, "closed on another photo than the one tapped: it fades and nothing flies")
        assertEquals(2, viewer.session?.page, "still drawn while it fades")

        viewer.onClosed()
        assertNull(viewer.session)
    }

    @Test
    fun `a second close and a finish while open do nothing`() {
        val viewer = MediaViewerController()
        var reports = 0
        viewer.open(photos, index = 0) { reports++ }

        viewer.onClosed()
        assertTrue(viewer.shown, "a finished fade-in is not a close")
        viewer.close()
        viewer.close()

        assertEquals(1, reports)
    }

    @Test
    fun `only the item tapped flies and paging hides no other thumbnail`() {
        val viewer = MediaViewerController()
        viewer.open(photos, index = 0)

        viewer.session?.onPageSettled(1)

        assertTrue(viewer.hidesThumbnail("photo-0"))
        assertFalse(viewer.hidesThumbnail("photo-1"))
        assertEquals("photo-0", viewer.flightKey)
    }

    @Test
    fun `closed on the item tapped it flies back to its thumbnail`() {
        val viewer = MediaViewerController()
        viewer.open(photos, index = 1)
        viewer.session?.onPageSettled(2)
        viewer.session?.onPageSettled(1)

        viewer.close()

        assertEquals("photo-1", viewer.flightKey)
        assertFalse(viewer.hidesThumbnail("photo-1"), "shown again for the page to land on")
        viewer.onClosed()
        assertNull(viewer.flightKey)
    }
}
