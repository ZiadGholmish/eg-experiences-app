package eg.bahr.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.collectLatest

/*
 * The loading shimmer (M3-M14), moved here from feature/trips' trip page.
 *
 * The effect is the handoff's own (HANDOFF screen 3 → "Loading state"; the prototypes' `sh` keyframes):
 * a `surfaceDim` fill whose opacity pulses .5 → .9 → .5 over 1.5s, ease-in-out. A pulse, not a
 * sweeping highlight, so it has no direction and needs nothing for RTL.
 *
 * Performance (jetpack-compose-performance skill): one clock for the whole app, not one infinite
 * transition per placeholder. [BahrTheme] owns a [BahrShimmerClock]; every placeholder drawn by
 * [bahrShimmer] reads the clock's alpha in its draw phase only, so a pulsing screen redraws its
 * placeholders each frame and never recomposes or re-lays them out. The clock only runs while at
 * least one placeholder is attached: a loaded screen asks for no frames at all. All placeholders on
 * screen share the one value, so they pulse in step even when they came in at different times
 * (a lazy list's items composed as they scroll in).
 *
 * Reduced motion: the clock never starts, and placeholders hold [BahrMotion.ShimmerAlphaStatic].
 */

/**
 * Drives every placeholder's alpha. Counts the placeholders attached ([attach] / [detach]) and runs
 * the pulse only while there is one.
 */
@Stable
internal class BahrShimmerClock {
    private val pulse = Animatable(BahrMotion.ShimmerAlphaLow)
    private var placeholders by mutableIntStateOf(0)

    /** Snapshot state: read in a draw phase, a change redraws only that placeholder. */
    val alpha: Float get() = pulse.value

    // A lazy list attaches its items' nodes inside its measure pass, which records state reads. The
    // count's read-then-write must not be recorded there, or every attach would re-measure the list.
    fun attach() {
        Snapshot.withoutReadObservation { placeholders++ }
    }

    fun detach() {
        Snapshot.withoutReadObservation { placeholders-- }
    }

    /** Runs for the theme's lifetime (a LaunchedEffect in [BahrTheme]). */
    suspend fun run(reducedMotion: Boolean) {
        if (reducedMotion) {
            pulse.snapTo(BahrMotion.ShimmerAlphaStatic)
            return
        }
        snapshotFlow { placeholders > 0 }.collectLatest { shimmering ->
            // Each loading state starts the pulse from its low end, as a fresh CSS animation would.
            pulse.snapTo(BahrMotion.ShimmerAlphaLow)
            if (shimmering) {
                pulse.animateTo(
                    targetValue = BahrMotion.ShimmerAlphaHigh,
                    animationSpec =
                        infiniteRepeatable(
                            tween(BahrMotion.Shimmer / 2, easing = BahrMotion.EaseInOut),
                            RepeatMode.Reverse,
                        ),
                )
            }
        }
    }
}

/** Null outside a [BahrTheme] (a bare preview): placeholders are then drawn still. */
internal val LocalBahrShimmerClock = staticCompositionLocalOf<BahrShimmerClock?> { null }

/**
 * Paints this element as a loading placeholder: the theme's `surfaceDim` in [shape], pulsing with
 * every other placeholder on screen (see the file comment). Content drawn by the element itself
 * goes on top. Prefer the blocks in `components/BahrSkeleton.kt`, which also size the placeholder.
 */
fun Modifier.bahrShimmer(shape: Shape = RectangleShape): Modifier = this then BahrShimmerElement(shape)

private data class BahrShimmerElement(
    val shape: Shape,
) : ModifierNodeElement<BahrShimmerNode>() {
    override fun create() = BahrShimmerNode(shape)

    override fun update(node: BahrShimmerNode) {
        node.update(shape)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bahrShimmer"
        properties["shape"] = shape
    }
}

private class BahrShimmerNode(
    private var shape: Shape,
) : Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode {
    private var clock: BahrShimmerClock? = null

    // The outline is rebuilt only when the size, direction or shape changes, not every frame.
    private var outline: Outline? = null
    private var outlineSize = Size.Unspecified
    private var outlineDirection: LayoutDirection? = null

    fun update(shape: Shape) {
        if (shape != this.shape) {
            this.shape = shape
            outline = null
            // The shape is not snapshot state, so nothing else would redraw this node with it.
            invalidateDraw()
        }
    }

    override fun onAttach() {
        onObservedReadsChanged()
    }

    // Observed, not read once: a new theme above (a new clock) moves this placeholder's count to it.
    override fun onObservedReadsChanged() {
        observeReads {
            val current = currentValueOf(LocalBahrShimmerClock)
            if (current !== clock) {
                clock?.detach()
                clock = current?.also { it.attach() }
            }
        }
    }

    override fun onDetach() {
        clock?.detach()
        clock = null
    }

    override fun ContentDrawScope.draw() {
        // Both reads happen in the draw phase, so a theme switch or a pulse step redraws this node only.
        val color = currentValueOf(LocalBahrColors).surfaceDim
        val alpha = clock?.alpha ?: BahrMotion.ShimmerAlphaStatic
        val cached = outline
        val shaped =
            if (cached != null && outlineSize == size && outlineDirection == layoutDirection) {
                cached
            } else {
                shape.createOutline(size, layoutDirection, this).also {
                    outline = it
                    outlineSize = size
                    outlineDirection = layoutDirection
                }
            }
        drawOutline(shaped, color, alpha = alpha)
        drawContent()
    }
}
