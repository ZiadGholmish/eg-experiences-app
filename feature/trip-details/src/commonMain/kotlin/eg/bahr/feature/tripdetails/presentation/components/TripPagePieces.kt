package eg.bahr.feature.tripdetails.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import eg.bahr.core.designsystem.components.BahrSkeletonLine
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrTheme

/*
 * Small parts shared by the trip page's sections. Private to the feature until a second screen needs
 * one (then it moves to core:designsystem).
 */

/** A filled circle with an icon in it: itinerary dots, included/excluded markers. */
@Composable
internal fun Marker(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(BahrSize.marker).clip(BahrTheme.shapes.full).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(BahrSize.iconSmall))
    }
}

/**
 * Room for [lines] lines of [style] text that has not arrived yet, a shimmer bar on each (MV-M7). The height is not
 * computed from the style's line height: an empty [Text] of the same style and line count reserves it, so it is exactly
 * what the text takes when it lands (font padding and line-height trim included) and nothing under it moves. The bars
 * span [widthFraction] of the width; a paragraph's last line is shorter.
 */
@Composable
internal fun LinesSkeleton(
    style: TextStyle,
    modifier: Modifier = Modifier,
    lines: Int = 1,
    widthFraction: Float = 1f,
) {
    // No semantics of its own: the reserved text clears its own, and a test tag on [modifier] stays findable.
    Box(modifier) {
        ReservedText(style = style, lines = lines, modifier = Modifier.fillMaxWidth(widthFraction))
        Column(Modifier.matchParentSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            repeat(lines) { line ->
                val width = if (lines > 1 && line == lines - 1) LAST_LINE_FRACTION else 1f
                BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = width))
            }
        }
    }
}

/**
 * Nothing drawn, [lines] lines of [style] high: what a text that is not known yet will take. Used where the ground
 * around it shimmers instead of the line (a fact cell still loading).
 */
@Composable
internal fun ReservedText(
    style: TextStyle,
    modifier: Modifier = Modifier,
    lines: Int = 1,
) {
    Text(text = NO_TEXT, style = style, minLines = lines, modifier = modifier.clearAndSetSemantics {})
}

/**
 * [text] laid out but not drawn: it holds the room a value of the same kind takes (e.g. "6 seats left", which wraps
 * on a narrow English date card), while a shimmer over it says the real one is on its way.
 */
@Composable
internal fun InvisibleText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = style, modifier = modifier.alpha(INVISIBLE).clearAndSetSemantics {})
}

/** A paragraph's last skeleton line, as in the prototype's skeleton. */
private const val LAST_LINE_FRACTION = 0.84f

/**
 * What a reserved line is measured with: a no-break space, a glyph of the style's own font. An empty string would be measured
 * with the platform's default font metrics instead, a few dp off the text that lands.
 */
private const val NO_TEXT = "\u00A0"
private const val INVISIBLE = 0f
