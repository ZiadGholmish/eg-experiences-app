package eg.bahr.feature.home.presentation

import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.feature.home.presentation.components.PagerDotsFrame
import eg.bahr.feature.home.presentation.components.PagerDotsGeometry
import eg.bahr.feature.home.presentation.components.PillSpan
import eg.bahr.feature.home.presentation.components.pagerDotsFrame
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M4-M14: the liquid dots' geometry, (page, offset, count, rtl) → where the pill and the dots are.
 * Sizes are the handoff's dp at 1x: a 7 dot, a 22 pill, a 4 gap, so a dot's step is 11 and the
 * first slot starts at 2 (half a gap).
 */
class PagerDotsFrameTest {
    private val geometry = PagerDotsGeometry(dot = 7f, active = 22f, gap = 4f)
    private val count = 4
    private val width = geometry.rowWidth(count) // 4 * 4 + 3 * 7 + 22 = 59

    private fun frame(
        page: Int,
        offset: Float = 0f,
        rtl: Boolean = false,
        reduced: Boolean = false,
    ) = pagerDotsFrame(page, offset, count, rtl, geometry, reduced)

    /** The pill as it shows: at rest on the last banner the first banner's piece is there, at alpha 0. */
    private fun PagerDotsFrame.pill(): PillSpan = pills.filter { it.alpha > EPSILON }.single()

    private fun assertNear(
        expected: Float,
        actual: Float,
        what: String,
    ) = assertTrue(abs(expected - actual) < EPSILON, "$what: expected $expected, was $actual")

    private fun assertSameFrame(
        expected: PagerDotsFrame,
        actual: PagerDotsFrame,
    ) {
        assertEquals(expected.pills.size, actual.pills.size, "pieces")
        expected.pills.zip(actual.pills).forEach { (e, a) ->
            assertNear(e.start, a.start, "start")
            assertNear(e.end, a.end, "end")
            assertNear(e.alpha, a.alpha, "alpha")
        }
        assertEquals(expected.dots.size, actual.dots.size, "dots")
        expected.dots.zip(actual.dots).forEach { (e, a) ->
            assertNear(e.center, a.center, "dot centre")
            assertNear(e.scale, a.scale, "dot scale")
        }
    }

    @Test
    fun `settled - the pill rests on its dot as wide as the handoff's`() {
        val pill = frame(page = 1).pill()

        assertNear(13f, pill.start, "start")
        assertNear(35f, pill.end, "end")
        assertEquals(1f, pill.thickness)
    }

    @Test
    fun `settled - the other dots sit a gap apart around the pill`() {
        val centres = frame(page = 1).dots.filter { it.scale == 1f }.map { it.center }

        // Dot 0 before the pill, dots 2 and 3 after it.
        assertEquals(listOf(5.5f, 42.5f, 53.5f), centres)
    }

    @Test
    fun `any lap - the page counts by its banner`() {
        assertSameFrame(frame(page = 1), frame(page = 1 + count * 500))
    }

    @Test
    fun `half-way - the pill stretches over both dots`() {
        val pill = frame(page = 1, offset = .5f).pill()

        // Resting at 1 it is 13..35; at 2 it will be 24..46. Mid-way it reaches nearly to 46 and still
        // starts near 13: one long, thinner pill.
        assertTrue(pill.start < 16f, "trailing edge has barely left: ${pill.start}")
        assertTrue(pill.end > 43f, "leading edge is nearly there: ${pill.end}")
        assertTrue(pill.thickness < 1f, "it thins as it stretches: ${pill.thickness}")
        assertTrue(pill.thickness >= BahrMotion.PagerPillStretchThickness)
    }

    @Test
    fun `the leading edge moves first - the trailing edge catches up`() {
        val early = frame(page = 1, offset = .25f).pill()
        val late = frame(page = 2, offset = -.25f).pill()

        assertTrue(early.end - 35f > early.start - 13f, "early on the end has moved more: $early")
        assertTrue(46f - late.end < 24f - late.start, "late on the start has more left to go: $late")
    }

    @Test
    fun `the pager's half-way flip of currentPage does not move the pill`() {
        // currentPage turns from 2 to 3 half-way through the swipe and its offset from +.5 to -.5.
        assertSameFrame(frame(page = 2, offset = .5f), frame(page = 3, offset = -.5f))
        assertSameFrame(frame(page = 1, offset = .45f), frame(page = 2, offset = -.55f))
    }

    @Test
    fun `the swipe ends where the next page rests`() {
        val arriving = frame(page = 1, offset = .9999f).pill()
        val rested = frame(page = 2).pill()

        assertNear(rested.start, arriving.start, "start")
        assertNear(rested.end, arriving.end, "end")
    }

    @Test
    fun `dragged back - the same frames in reverse`() {
        assertSameFrame(frame(page = 1, offset = .3f), frame(page = 2, offset = -.7f))
    }

    @Test
    fun `the pill only ever moves forward - edges never go back during a swipe`() {
        var previous = frame(page = 0).pill()
        for (step in 1..STEPS) {
            val t = step.toFloat() / STEPS
            val pill = frame(page = 0, offset = t).pill()
            assertTrue(pill.start >= previous.start - EPSILON && pill.end >= previous.end - EPSILON, "at $t: $previous → $pill")
            previous = pill
        }
    }

    @Test
    fun `the dot the pill reaches shrinks - the one it leaves grows back`() {
        val start = frame(page = 1, offset = .01f).dots
        val end = frame(page = 2, offset = -.01f).dots

        // Dot 2 (centre 42.5) is being covered; dot 1 (centre 16.5) is being let go.
        assertTrue(start.single { it.center == 42.5f }.scale > end.single { it.center == 42.5f }.scale)
        assertTrue(start.single { it.center == 16.5f }.scale < end.single { it.center == 16.5f }.scale)
    }

    // ---------- The wrap: last → first ----------

    @Test
    fun `wrap - starts as the last pill at rest and ends as the first`() {
        val last = frame(page = 3)
        val leaving = frame(page = 3, offset = .0001f)
        val arrived = frame(page = 4)
        val arriving = frame(page = 3, offset = .9999f)

        assertNear(last.pill().start, leaving.pill().start, "leaving start")
        assertNear(last.pill().end, leaving.pill().end, "leaving end")
        assertNear(arrived.pill().start, arriving.pill().start, "arriving start")
        assertNear(arrived.pill().end, arriving.pill().end, "arriving end")
    }

    @Test
    fun `wrap - forward - never sweeps back across the middle dots`() {
        val first = frame(page = 0).pill()
        val last = frame(page = 3).pill()
        for (step in 1 until STEPS) {
            val t = step.toFloat() / STEPS
            val f = frame(page = 3, offset = t)
            val (leaving, arriving) = f.pills
            // The leaving piece stays at the end, inside the last resting pill; the arriving piece at
            // the start, inside the first. Nothing of the pill is ever over the middle of the row.
            assertTrue(leaving.start >= last.start - EPSILON && leaving.end <= last.end + EPSILON, "at $t leaving $leaving")
            assertTrue(arriving.start >= first.start - EPSILON && arriving.end <= first.end + EPSILON, "at $t arriving $arriving")
            // The middle dots keep a gap from both pieces.
            val middle = f.dots.drop(1).dropLast(1)
            middle.forEach { dot ->
                assertTrue(dot.center - geometry.dot / 2 >= arriving.end + geometry.gap - EPSILON, "at $t dot $dot vs $arriving")
                assertTrue(dot.center + geometry.dot / 2 <= leaving.start - geometry.gap + EPSILON, "at $t dot $dot vs $leaving")
            }
        }
    }

    @Test
    fun `wrap - the middle dots glide forward one slot's difference - never back`() {
        var previous = frame(page = 3, offset = 0f).dots[1].center
        for (step in 1..STEPS) {
            val centre = frame(page = 3, offset = step.toFloat() / STEPS).dots[1].center
            assertTrue(centre >= previous - EPSILON, "dot 1 went back: $previous → $centre")
            previous = centre
        }
        assertNear(16.5f, frame(page = 3, offset = .0001f).dots[1].center, "dot 1 starts by the first dot")
        assertNear(31.5f, previous, "and ends after the first pill")
    }

    @Test
    fun `wrap - the pill is never gone - one piece is always fully drawn`() {
        for (step in 0..STEPS) {
            val f = frame(page = 3, offset = step.toFloat() / STEPS)
            assertTrue(f.pills.any { it.alpha == 1f }, "at ${step.toFloat() / STEPS}: ${f.pills}")
        }
    }

    @Test
    fun `wrap - also across the half-way flip and from any lap`() {
        assertSameFrame(frame(page = 3, offset = .5f), frame(page = 4, offset = -.5f))
        assertSameFrame(frame(page = 3, offset = .2f), frame(page = 3 + count * 700, offset = .2f))
        // currentPage already on the first banner, offset still negative: the same wrap.
        assertSameFrame(frame(page = 3, offset = .8f), frame(page = 0, offset = -.2f))
    }

    // ---------- RTL ----------

    @Test
    fun `rtl - every frame is the ltr one mirrored`() {
        for ((page, offset) in listOf(0 to 0f, 1 to .5f, 2 to -.3f, 3 to .4f, 3 to .5f)) {
            val ltr = frame(page, offset)
            val rtl = frame(page, offset, rtl = true)
            assertSameFrame(
                PagerDotsFrame(
                    pills = ltr.pills.map { it.copy(start = width - it.end, end = width - it.start) },
                    dots = ltr.dots.map { it.copy(center = width - it.center) },
                ),
                rtl,
            )
        }
    }

    @Test
    fun `rtl - the first banner's pill is at the right - the next is to its left`() {
        val first = frame(page = 0, rtl = true).pill()
        val half = frame(page = 0, offset = .5f, rtl = true).pill()

        assertNear(width - 2f, first.end, "first pill's right edge")
        assertTrue(half.start < first.start, "it stretches leftwards: $half")
        assertTrue(first.end - half.end < geometry.dot / 2, "its trailing (right) edge has barely left: $half")
    }

    // ---------- Reduce motion ----------

    @Test
    fun `reduce motion - no stretch - the pill rests on the nearer dot`() {
        val pillOnly = { f: PagerDotsFrame -> PagerDotsFrame(listOf(f.pill()), emptyList()) }
        assertSameFrame(pillOnly(frame(page = 1)), pillOnly(frame(page = 1, offset = .3f, reduced = true)))
        assertSameFrame(pillOnly(frame(page = 2)), pillOnly(frame(page = 1, offset = .6f, reduced = true)))
        assertSameFrame(pillOnly(frame(page = 0)), pillOnly(frame(page = 3, offset = .7f, reduced = true)))
        assertEquals(count - 1, frame(page = 1, offset = .3f, reduced = true).dots.count { it.scale == 1f && it.alpha == 1f })
    }

    private companion object {
        const val EPSILON = .01f
        const val STEPS = 50
    }
}
