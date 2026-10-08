package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme

/**
 * Tinted status pill: a category on a trip card, "Verified host", a booking's status.
 *
 * Not in the handoff (it only has the seat-specific [SeatBadge]); kept from the provisional
 * `EgBadge` because three screens use it, and rebuilt on Bahr tokens. The tone decides the
 * container/content pair, so a caller cannot pass raw colours.
 */
interface BahrBadge {
    /** Mirrors the contract's `Tone` enum (openapi.yaml), so a server-sent tone maps one-to-one. */
    enum class Tone { Primary, Secondary, Tertiary, Quaternary, Success }

    @Immutable
    data class Props(
        val text: String,
        val tone: Tone = Tone.Primary,
    )
}

@Composable
fun BahrBadge(
    modifier: Modifier = Modifier,
    props: BahrBadge.Props,
) {
    val (container, content) = props.tone.colors()
    Text(
        text = props.text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        modifier =
            modifier
                .clip(BahrTheme.shapes.full)
                .background(container)
                .padding(horizontal = BahrSpacing.sm, vertical = BahrSpacing.xs),
    )
}

@Composable
private fun BahrBadge.Tone.colors(): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val x = BahrTheme.colors
    return when (this) {
        BahrBadge.Tone.Primary -> c.primaryContainer to c.onPrimaryContainer
        BahrBadge.Tone.Secondary -> c.secondaryContainer to c.onSecondaryContainer
        // tertiaryContainer is the hold / sold-out ground (tokens.json), not the action colour.
        BahrBadge.Tone.Tertiary -> c.tertiaryContainer to c.onTertiaryContainer
        BahrBadge.Tone.Quaternary -> x.quaternaryContainer to x.onQuaternaryContainer
        BahrBadge.Tone.Success -> x.successContainer to x.onSuccessContainer
    }
}
