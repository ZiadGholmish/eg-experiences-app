package eg.bahr.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShimmer
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.a11y_busy
import org.jetbrains.compose.resources.stringResource

/*
 * Skeleton building blocks (M3-M14): the placeholders a loading screen is drawn from, each a
 * pulsing `surfaceDim` shape (theme/BahrShimmer.kt). A screen's skeleton mirrors its loaded layout
 * with these, so nothing jumps when the content arrives, and keeps the text-first rule (HANDOFF
 * §loading): what is already known is drawn as text; only what is not shimmers.
 */

/**
 * The root of a loading region (a list's first page, Home's sections, a form still loading). For
 * screen readers it is one "Loading" node instead of silence, as the spinner it replaces was: it
 * clears the semantics of everything inside. That includes fixed text drawn in the region (Home's
 * "All trips", the booking form's "Pick a date", the edit form's labels), which screen readers do not
 * hear until the content arrives. The trip and host pages do not use it, since there the known text
 * (title, price) is the page's real content and must stay readable while the rest loads.
 */
@Composable
fun BahrSkeleton(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(BahrSpacing.lg),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val busy = stringResource(Res.string.a11y_busy)
    Column(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = busy
                testTag = BAHR_SKELETON_TAG
            },
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** For tests: the root of a [BahrSkeleton]. */
const val BAHR_SKELETON_TAG = "bahr_skeleton"

/** One line of text still loading: [BahrSize.skeletonLine] tall, rounded like the prototypes' 6px lines. */
interface BahrSkeletonLine {
    @Immutable
    data class Props(
        /** Share of the available width; a paragraph's last line is shorter. */
        val widthFraction: Float = 1f,
        val height: Dp = BahrSize.skeletonLine,
    )
}

@Composable
fun BahrSkeletonLine(
    modifier: Modifier = Modifier,
    props: BahrSkeletonLine.Props = BahrSkeletonLine.Props(),
) {
    Box(
        modifier
            .fillMaxWidth(props.widthFraction)
            .height(props.height)
            .bahrShimmer(BahrTheme.shapes.extraSmall),
    )
}

/**
 * A photo, a card's picture, a tile: any rectangle still loading. The caller sizes it (an aspect
 * ratio for photos, so the reserved room is the loaded photo's).
 */
interface BahrSkeletonBlock {
    @Immutable
    data class Props(
        val shape: Shape = RectangleShape,
    )
}

@Composable
fun BahrSkeletonBlock(
    modifier: Modifier = Modifier,
    props: BahrSkeletonBlock.Props = BahrSkeletonBlock.Props(),
) {
    Box(modifier.bahrShimmer(props.shape))
}

/** An avatar or icon disc still loading. */
interface BahrSkeletonCircle {
    @Immutable
    data class Props(
        val size: Dp,
    )
}

@Composable
fun BahrSkeletonCircle(
    modifier: Modifier = Modifier,
    props: BahrSkeletonCircle.Props,
) {
    Box(modifier.size(props.size).bahrShimmer(BahrTheme.shapes.full))
}

/**
 * A card still loading: the [BahrCard] itself (its radius, `level1` shadow and HighContrast outline)
 * is drawn for real, so the page's rhythm is there at once; the placeholders go inside, as the
 * loaded card's content would.
 */
@Composable
fun BahrSkeletonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BahrCard(modifier = modifier.fillMaxWidth(), content = content)
}
