package eg.bahr.core.catalog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import eg.bahr.core.designsystem.components.BahrBackButton
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme

/*
 * Page parts two or more of the trip-area features share (moved from feature:trips when it was split, M3-M16,
 * unchanged): the list pages' back bar, a paragraph's skeleton lines, the round initial avatar, and the host's
 * shared-element key that joins the trip page's host row to the host page's header.
 */

/**
 * The app bar of a list opened from Home: a round back button. The list's title sits in the larger
 * heading under it. Carries the status-bar inset itself (edge-to-edge; each screen handles its own
 * top inset).
 */
@Composable
fun ListTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
    ) {
        BahrBackButton(props = BahrBackButton.Props(onClick = onBack))
    }
}

/** Skeleton text lines, for the deck and the itinerary while they load. */
@Composable
fun TextSkeleton(
    lines: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        repeat(lines) { line ->
            // The last line is shorter, as a paragraph's is.
            val width = if (line == lines - 1) LAST_LINE_FRACTION else 1f
            BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = width))
        }
    }
}

/**
 * A round avatar: the photo when there is one, over the person's initial on a tinted ground (which
 * is also what shows while the photo loads, or when the backend serves none, as today for hosts).
 */
@Composable
fun InitialAvatar(
    name: String?,
    tint: Tint,
    size: Dp,
    image: String? = null,
) {
    val colors = tint.colors()
    Box(
        modifier = Modifier.size(size).clip(BahrTheme.shapes.full).background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        name?.firstOrNull()?.let {
            Text(text = it.uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.content)
        }
        image?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * The shared-element key of a host's avatar and name: the trip page's host row and the host page's
 * header carry the same one, so the row grows into the header on the way in (M4-M6 motion, M4-M10).
 */
fun hostSharedKey(slug: String): String = "host:$slug"

private const val LAST_LINE_FRACTION = .6f
