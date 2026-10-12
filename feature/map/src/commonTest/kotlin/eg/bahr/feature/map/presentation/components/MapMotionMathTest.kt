package eg.bahr.feature.map.presentation.components

import eg.bahr.feature.map.data.MapFixtures
import eg.bahr.feature.map.presentation.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M4-M9: the camera a map is created with ([cameraToFit]: framed on the pins, never the world), and
 * the side a newly picked pin's card comes in from ([cardEntrySide]).
 */
class MapMotionMathTest {
    private val none = MapInsets(0f, 0f, 0f, 0f)

    @Test
    fun `a quarter of the world's width fills a 256 wide map at zoom 2`() {
        // Zoom 0: the world is 256 wide; zoom 2: 1024, of which a quarter is 256.
        val pose = cameraToFit(GeoBounds(south = -1.0, west = 0.0, north = 1.0, east = 90.0), 256f, 1_000f, none, edge = 0f)

        assertEquals(2f, pose.zoom, absoluteTolerance = .001f)
        assertEquals(45.0, pose.target.lng, absoluteTolerance = 1e-9)
        assertEquals(0.0, pose.target.lat, absoluteTolerance = 1e-9)
    }

    @Test
    fun `the chrome and the edge are kept clear`() {
        val bounds = GeoBounds(south = -1.0, west = 0.0, north = 1.0, east = 90.0)

        val pose = cameraToFit(bounds, 512f, 1_000f, MapInsets(left = 100f, top = 0f, right = 100f, bottom = 0f), edge = 28f)

        // 512 - 100 - 100 - 2 x 28 = 256 across: zoom 2 again.
        assertEquals(2f, pose.zoom, absoluteTolerance = .001f)
    }

    @Test
    fun `the taller side decides - and the middle is Mercator's not the latitudes' average`() {
        val pose = cameraToFit(GeoBounds(south = 0.0, west = 0.0, north = 60.0, east = 1.0), 1_000f, 256f, none, edge = 0f)

        // On a Mercator map 60°N is further from 30°N than the equator is, so the middle is north of 30°.
        assertTrue(pose.target.lat > 30.0, "middle at ${pose.target.lat}")
        val byHeight = cameraToFit(GeoBounds(south = 0.0, west = 0.0, north = 60.0, east = 1.0), 1_000f, 512f, none, edge = 0f)
        assertEquals(1f, byHeight.zoom - pose.zoom, absoluteTolerance = .001f, "twice the height, one zoom level more")
    }

    @Test
    fun `with no room left it shows the box's middle at the country-wide zoom`() {
        val bounds = GeoBounds(south = 29.0, west = 30.0, north = 31.0, east = 32.0)

        val pose = cameraToFit(bounds, 300f, 200f, MapInsets(left = 0f, top = 120f, right = 0f, bottom = 120f), edge = 10f)

        assertEquals(FALLBACK_ZOOM, pose.zoom)
        assertEquals(31.0, pose.target.lng, absoluteTolerance = 1e-9)
    }

    @Test
    fun `the catalogue's pins open at a country zoom - not the world's`() {
        val pins = MapFixtures.map().pins.map { GeoPoint(it.lat, it.lng) }
        val bounds = GeoBounds.around(pins)!!

        val pose = cameraToFit(bounds, 360f, 560f, MapInsets(left = 0f, top = 64f, right = 0f, bottom = 72f), edge = 40f)

        assertTrue(pose.zoom in 4f..8f, "zoom ${pose.zoom}")
        assertTrue(pose.target.lat in bounds.south..bounds.north && pose.target.lng in bounds.west..bounds.east)
    }

    @Test
    fun `a pin to the east brings its card in from the right - to the west from the left`() {
        val dawn = MapFixtures.pin("burullus-dawn", lat = 31.503, lng = 30.804)
        val beach = MapFixtures.pin("ain-sokhna-beach-day", lat = 29.6, lng = 32.34)

        assertEquals(1, cardEntrySide(from = dawn, to = beach, rtl = false))
        assertEquals(-1, cardEntrySide(from = beach, to = dawn, rtl = false))
        // The map is not mirrored in Arabic: east is still to the right (PLAN M4-M9, clarified 2026-10-11).
        assertEquals(1, cardEntrySide(from = dawn, to = beach, rtl = true))
        assertEquals(-1, cardEntrySide(from = beach, to = dawn, rtl = true))
    }

    @Test
    fun `on the same meridian the card comes in from the end side`() {
        val north = MapFixtures.pin("north", lat = 31.0, lng = 30.0)
        val south = MapFixtures.pin("south", lat = 29.0, lng = 30.0)

        assertEquals(1, cardEntrySide(from = north, to = south, rtl = false), "English: from the right")
        assertEquals(-1, cardEntrySide(from = north, to = south, rtl = true), "Arabic: from the left")
    }

    @Test
    fun `a pin under the card is moved up just past its top - one already clear is not moved`() {
        // An 800 high map with a 300 card: the card's top is at 500.
        assertEquals(30f, revealShift(anchorY = 520f, height = 800f, bottomInset = 300f, clearance = 10f))
        assertEquals(0f, revealShift(anchorY = 400f, height = 800f, bottomInset = 300f, clearance = 10f))
        assertEquals(0f, revealShift(anchorY = 490f, height = 800f, bottomInset = 300f, clearance = 10f), "exactly clear")
    }

    @Test
    fun `a padding change is cancelled by half of it`() {
        val legend = MapInsets(left = 0f, top = 60f, right = 0f, bottom = 80f)
        val card = legend.copy(bottom = 280f)

        // 200 more at the bottom re-centres the map 100 up: the camera moves 100 up (negative y) to undo it.
        assertEquals(0f to -100f, keepStillShift(legend, card))
        assertEquals(0f to 100f, keepStillShift(card, legend))
        assertEquals(15f to 0f, keepStillShift(legend, legend.copy(left = 30f)))
    }

    @Test
    fun `a pan runs once per request`() {
        val dawn = PinReveal("burullus-dawn", GeoPoint(31.503, 30.804), clearancePx = 20f, cardHeightPx = 400)

        assertTrue(shouldReveal(dawn, lastHandled = null), "a new request pans")
        assertFalse(shouldReveal(dawn, lastHandled = dawn), "the same request again (an effect run again): no second pan")
        assertTrue(shouldReveal(dawn.copy(cardHeightPx = 420), lastHandled = dawn), "the card measured again pans again")
        assertFalse(shouldReveal(null, lastHandled = dawn), "no pin picked: nothing to pan")
    }
}
