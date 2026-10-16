package eg.bahr.feature.home.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastForEach
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.home_slide_of
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.min

/**
 * The handoff's dot indicator, liquid (M4-M14): the active pill follows the pager's live scroll
 * position ([page] + [offset], the pager's `currentPage` and `currentPageOffsetFraction`), so it
 * tracks a drag, a fling and the auto-advance rather than jumping when a page settles. How it moves
 * is [pagerDotsFrame]'s; this only lays the dots out and draws the frame.
 *
 * The dots and the pill are drawn in one draw modifier on the row, and [page] and [offset] are read
 * only there, so a scroll redraws the row without recomposing anything. The cells under it are the
 * dots' touch targets and screen-reader tabs, laid out as before (the current page's cell is the
 * pill's width): each sits in a cell as tall as the touch minimum, and Compose widens a target
 * narrower than the minimum for touch on its own (the nearest dot wins where two overlap), so a dot
 * is easy to hit without spreading the row out. [current] (the page the pager counts as current)
 * says which tab is selected; it changes once a page, half-way through a swipe.
 *
 * Each dot is a tab that goes to its slide ([onSelect], HANDOFF Home: "tappable"), labelled
 * "Slide n of N". Internal, not private, so the screenshot tests can draw it at a fixed position.
 */
@Composable
internal fun PagerDots(
    count: Int,
    current: Int,
    page: () -> Int,
    offset: () -> Float,
    onSelect: (banner: Int) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduced = BahrTheme.reducedMotion
    val pillColor = MaterialTheme.colorScheme.primary
    val dotColor = BahrTheme.colors.track

    fun DrawScope.drawFrame(
        geometry: PagerDotsGeometry,
        page: Int,
        offset: Float,
    ) {
        val frame = pagerDotsFrame(page, offset, count, rtl, geometry, reduced)
        frame.dots.fastForEach { drawDot(it, geometry, dotColor) }
        frame.pills.fastForEach { drawPill(it, geometry, pillColor) }
    }

    // The geometry depends on the density only: built once per size, not on every frame of a swipe
    // (Compose audit L6). The live position is still read only here, in the draw phase.
    fun Modifier.drawsFrame(
        framePage: () -> Int,
        frameOffset: () -> Float,
    ): Modifier =
        drawWithCache {
            val geometry =
                PagerDotsGeometry(
                    dot = BahrSize.pagerDot.toPx(),
                    active = BahrSize.pagerDotActive.toPx(),
                    gap = BahrSpacing.xs.toPx(),
                )
            onDrawBehind { drawFrame(geometry, framePage(), frameOffset()) }
        }
    Box {
        if (reduced) {
            // Reduce motion: the pill does not travel. It snaps to the current page's dot, which the
            // pager moves on half-way through a swipe, and the two resting rows cross-fade (a fade is
            // not motion). Android's "Remove animations" scales this to nothing as well.
            Crossfade(targetState = current, animationSpec = tween(BahrMotion.Short), modifier = Modifier.matchParentSize()) { shown ->
                Box(Modifier.fillMaxSize().drawsFrame({ shown }, { 0f }))
            }
        }
        DotCells(
            count = count,
            current = current,
            onSelect = onSelect,
            modifier = if (reduced) Modifier else Modifier.drawsFrame(page, offset),
        )
    }
}

/**
 * The dots' touch targets and screen-reader tabs, laid out as before M4-M14 (the current page's cell
 * is the pill's width) but drawing nothing themselves: the row's width is what the frame is drawn in.
 */
@Composable
private fun DotCells(
    count: Int,
    current: Int,
    onSelect: (banner: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.selectableGroup()) {
        repeat(count) { index ->
            val active = index == current
            val label = stringResource(Res.string.home_slide_of, index + 1, count)
            Box(
                modifier =
                    Modifier
                        .heightIn(min = BahrSpacing.minTouch)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                        .semantics { contentDescription = label }
                        // Half the handoff's gap on each side, so neighbours sit the full gap apart.
                        .padding(horizontal = BahrSpacing.xs / 2),
                contentAlignment = Alignment.Center,
            ) {
                // Only takes the room: the row's draw modifier draws every dot and the pill.
                Box(Modifier.size(width = if (active) BahrSize.pagerDotActive else BahrSize.pagerDot, height = BahrSize.pagerDot))
            }
        }
    }
}

private fun DrawScope.drawDot(
    mark: DotMark,
    geometry: PagerDotsGeometry,
    color: Color,
) {
    drawCircle(
        color = color,
        radius = geometry.dot / 2 * mark.scale,
        center = Offset(mark.center, size.height / 2),
        alpha = mark.alpha,
    )
}

private fun DrawScope.drawPill(
    pill: PillSpan,
    geometry: PagerDotsGeometry,
    color: Color,
) {
    val width = pill.end - pill.start
    // Never taller than wide: a sliver of pill is a small round drop, not a vertical bar.
    val height = min(geometry.dot * pill.thickness, width)
    drawRoundRect(
        color = color,
        topLeft = Offset(pill.start, (size.height - height) / 2),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2),
        alpha = pill.alpha,
    )
}

/** The dots' sizes in px: a dot, the active pill's resting width, and the gap between two of them. */
internal data class PagerDotsGeometry(
    val dot: Float,
    val active: Float,
    val gap: Float,
) {
    /** From one dot's start to the next one's. */
    val step: Float get() = dot + gap

    /** How much wider the active slot is than a dot. */
    val stretch: Float get() = active - dot

    /** The row: every slot, the active one included, with half a gap at either end (the cells' padding). */
    fun rowWidth(count: Int): Float = count * gap + (count - 1) * dot + active
}

/** A piece of the active pill, from [start] to [end] in px from the row's left edge. */
internal data class PillSpan(
    val start: Float,
    val end: Float,
    /** Its height as a share of a dot's. */
    val thickness: Float = 1f,
    val alpha: Float = 1f,
)

/** An inactive dot, centred [center] px from the row's left edge. */
internal data class DotMark(
    val center: Float,
    val scale: Float = 1f,
    val alpha: Float = 1f,
)

/** What to draw for one scroll position: the pill (two pieces at the wrap) and the dots. */
internal data class PagerDotsFrame(
    val pills: List<PillSpan>,
    val dots: List<DotMark>,
)

/**
 * The dots for the pager at [page] (any lap) scrolled by [offset] pages (`currentPageOffsetFraction`,
 * -.5 to .5), for [count] banners, in px from the row's left edge.
 *
 * The page and the offset become one position on the banners, so the frame is continuous where the
 * pager flips `currentPage` half-way through a swipe. Between banner p and p + 1, at t of the way:
 * - **Worm.** The pill's leading edge moves first and its trailing edge catches up
 *   ([BahrMotion.PagerPillEdgeShare]), so in the middle it covers both dots, thinned
 *   ([BahrMotion.PagerPillStretchThickness]), then contracts into the next dot's resting pill. Dragged
 *   back, the same frames run in reverse, so the leading edge is the one on the way back.
 * - The dot the pill reaches shrinks as it is covered, the one it leaves grows back from under it
 *   ([BahrMotion.PagerDotPassScale]). The rest stay put.
 * - **Wrap**, from the last banner to the first: the pill does not sweep back across the row. It
 *   contracts out of the last dot towards the end and fades, while the first dot grows into the pill
 *   from the start edge and every dot between glides one slot forward to make room. Everything moves
 *   the way the slides do.
 * - **Reduce motion**: nothing moves; the pill rests on the nearer dot. [PagerDots] cross-fades the
 *   two resting rows when it changes.
 *
 * Laid out left to right, then mirrored for [rtl], where the first banner's dot is on the right.
 */
internal fun pagerDotsFrame(
    page: Int,
    offset: Float,
    count: Int,
    rtl: Boolean,
    geometry: PagerDotsGeometry,
    reducedMotion: Boolean = false,
): PagerDotsFrame {
    if (count < 1) return PagerDotsFrame(emptyList(), emptyList())
    // Banner index first, so a page thousands of laps in keeps its fraction exact.
    val position = page.mod(count) + offset
    val from = floor(position).toInt()
    val t = position - from
    val fromDot = from.mod(count)
    val toDot = (fromDot + 1) % count
    val frame =
        when {
            count == 1 -> resting(0, count, geometry)
            // Whichever dot the position is nearer: where the pager's currentPage flips.
            reducedMotion -> resting(if (t < HALF_WAY) fromDot else toDot, count, geometry)
            toDot == 0 -> wrap(t, count, geometry)
            else -> worm(fromDot, t, count, geometry)
        }
    return if (rtl) frame.mirrored(geometry.rowWidth(count)) else frame
}

/** Where dot [index]'s slot starts while [active] is the wide one. */
private fun PagerDotsGeometry.slotStart(
    index: Int,
    active: Int,
): Float = gap / 2 + index * step + if (index > active) stretch else 0f

/** The settled frame: [active]'s pill at rest, every other dot full size. */
private fun resting(
    active: Int,
    count: Int,
    geometry: PagerDotsGeometry,
): PagerDotsFrame {
    val start = geometry.slotStart(active, active)
    return PagerDotsFrame(
        pills = listOf(PillSpan(start, start + geometry.active)),
        dots = (0 until count).filter { it != active }.map { DotMark(geometry.slotStart(it, active) + geometry.dot / 2) },
    )
}

private fun worm(
    from: Int,
    t: Float,
    count: Int,
    geometry: PagerDotsGeometry,
): PagerDotsFrame {
    val share = BahrMotion.PagerPillEdgeShare
    val lead = eased(t / share)
    val trail = eased((t - (1 - share)) / share)
    val restingStart = geometry.slotStart(from, from)
    val start = restingStart + geometry.step * trail
    val end = restingStart + geometry.active + geometry.step * lead
    val stretched = ((end - start - geometry.active) / geometry.step).coerceIn(0f, 1f)
    val thickness = lerp(1f, BahrMotion.PagerPillStretchThickness, stretched)
    val dots =
        (0 until count).map { index ->
            val scale =
                when (index) {
                    from -> lerp(BahrMotion.PagerDotPassScale, 1f, trail)
                    from + 1 -> lerp(1f, BahrMotion.PagerDotPassScale, lead)
                    else -> 1f
                }
            DotMark(geometry.slotStart(index, from) + geometry.dot / 2, scale)
        }
    return PagerDotsFrame(listOf(PillSpan(start, end, thickness)), dots)
}

/**
 * Last banner to first. The last slot narrows from the pill's width to a dot's while the first widens
 * from a dot's to the pill's, so the row keeps its width and every dot between moves by the
 * difference, towards the end. A piece of pill fills each of the two slots, the leaving one fading
 * out over the second half and the arriving one fading in over the first, so the pill never vanishes.
 */
private fun wrap(
    t: Float,
    count: Int,
    geometry: PagerDotsGeometry,
): PagerDotsFrame {
    val u = eased(t)
    val shift = geometry.stretch * u
    val last = count - 1
    val lastStart = geometry.slotStart(last, last) + shift
    val rowEnd = geometry.slotStart(last, last) + geometry.active
    val firstStart = geometry.slotStart(0, 0)
    val pills =
        listOf(
            PillSpan(lastStart, rowEnd, alpha = (2 * (1 - u)).coerceAtMost(1f)),
            PillSpan(firstStart, firstStart + geometry.dot + shift, alpha = (2 * u).coerceAtMost(1f)),
        )
    val dots =
        (0 until count).map { index ->
            val left = if (index == 0) firstStart else geometry.slotStart(index, last) + shift
            val scale =
                when (index) {
                    0 -> lerp(1f, BahrMotion.PagerDotPassScale, u)
                    last -> lerp(BahrMotion.PagerDotPassScale, 1f, u)
                    else -> 1f
                }
            DotMark(left + geometry.dot / 2, scale)
        }
    return PagerDotsFrame(pills, dots)
}

private fun PagerDotsFrame.mirrored(width: Float) =
    PagerDotsFrame(
        pills = pills.map { it.copy(start = width - it.end, end = width - it.start) },
        dots = dots.map { it.copy(center = width - it.center) },
    )

/** [BahrMotion.EaseInOut] over a progress clamped to 0..1, so each edge starts and stops softly. */
private fun eased(progress: Float): Float = BahrMotion.EaseInOut.transform(progress.coerceIn(0f, 1f))

private fun lerp(
    from: Float,
    to: Float,
    fraction: Float,
): Float = from + (to - from) * fraction

/** Half-way between two banners, where the pager's `currentPage` moves on. */
private const val HALF_WAY = .5f
