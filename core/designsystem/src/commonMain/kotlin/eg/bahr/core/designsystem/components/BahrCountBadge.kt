package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrTheme

/**
 * The unread count pill (handoff §1): 18dp tall, at least as wide, 11sp at 800 with tabular figures, white
 * on `badge`, ringed 2dp in the colour behind it ([Props.ring]) so it stands off the icon it sits on.
 * Shows [BahrNavigationBar.badgeText] of the count ("1"–"9", then "9+"); draws nothing for none.
 *
 * Silent for screen readers: whoever carries it says the count in its own label instead
 * ([bahrNavigationSpokenLabel]: "Notifications, 3 unread"), so it is heard once.
 *
 * Promoted from the bottom bar's private badge when Home's Alerts bell (M3-M6a) became its second caller:
 * the bar rings it in `navSurface`, Home's top bar in the page colour.
 */
interface BahrCountBadge {
    @Immutable
    data class Props(
        /** Unread items; null or 0 shows none. */
        val count: Int?,
        /** The colour of what the badge sits on, for its ring. */
        val ring: Color,
    )
}

@Composable
fun BahrCountBadge(
    modifier: Modifier = Modifier,
    props: BahrCountBadge.Props,
) {
    val text = BahrNavigationBar.badgeText(props.count) ?: return
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.full
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clearAndSetSemantics {}
                .heightIn(min = BADGE_HEIGHT.dp)
                .widthIn(min = BADGE_HEIGHT.dp)
                .border(BahrBorder.selected, props.ring, shape)
                .padding(BahrBorder.selected)
                .clip(shape)
                .background(x.badge)
                .padding(horizontal = BADGE_PADDING.dp),
    ) {
        Text(
            text = text,
            color = x.onBadge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // "9+" stays "9+" in Arabic too (a bare RTL run would draw it "+9").
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum",
                    textDirection = TextDirection.Ltr,
                ),
        )
    }
}

/** The pill's 18px plus the 2px ring on each side, as the prototype's box-shadow ring adds it. */
private const val BADGE_HEIGHT = 22

/** The prototype's `padding: 0 5px` inside the pill. */
private const val BADGE_PADDING = 3
