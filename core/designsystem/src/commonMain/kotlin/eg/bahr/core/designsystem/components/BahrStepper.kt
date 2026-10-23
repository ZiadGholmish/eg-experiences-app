package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrElevation
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.bahrShadow

/**
 * A − count + stepper on a pill track (HANDOFF screen 4's party stepper): a light − button, the count,
 * a `primary` + button. Each end disables itself at its bound; the count is announced when it changes.
 *
 * Promoted in M3-M0 from two private copies that had drifted (M2-M3 review #3): date + party
 * (feature:booking) and the waiting-list form on the trip page (feature:trip-details). The copy is the
 * caller's, so the component knows nothing about people or seats.
 */
interface BahrStepper {
    /**
     * Where the stepper sits. [Page]: on the page's white or off-white, the handoff's
     * `surfaceContainer` track with a raised white − button. [Tint]: on a tinted panel (the coral
     * sold-out notice), where that track would vanish, so the track is white and the − button flat.
     */
    enum class Ground { Page, Tint }

    @Immutable
    data class Props(
        val value: Int,
        val canDecrease: Boolean,
        val canIncrease: Boolean,
        /** Read by screen readers for the − and + buttons. */
        val decreaseLabel: String,
        val increaseLabel: String,
        /** The count as spoken, e.g. "3 people"; announced politely on each change. */
        val valueDescription: String,
        val ground: Ground = Ground.Page,
        val onDecrease: () -> Unit = {},
        val onIncrease: () -> Unit = {},
    )
}

@Composable
fun BahrStepper(
    modifier: Modifier = Modifier,
    props: BahrStepper.Props,
) {
    val c = MaterialTheme.colorScheme
    val onTint = props.ground == BahrStepper.Ground.Tint
    Row(
        modifier =
            modifier
                .clip(BahrTheme.shapes.full)
                .background(if (onTint) c.surface else c.surfaceContainer)
                .padding(horizontal = BahrSpacing.sm, vertical = BahrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        StepperButton(
            icon = BahrIcons.Remove.outlined(),
            description = props.decreaseLabel,
            enabled = props.canDecrease,
            background = if (onTint) c.surfaceContainer else c.surface,
            content = c.onSurface,
            raised = !onTint,
            onClick = props.onDecrease,
        )
        Text(
            text = props.value.toString(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.widthIn(min = BahrSize.stepperValue).semantics {
                    contentDescription = props.valueDescription
                    liveRegion = LiveRegionMode.Polite
                },
        )
        StepperButton(
            icon = BahrIcons.Add.outlined(),
            description = props.increaseLabel,
            enabled = props.canIncrease,
            background = c.primary,
            content = c.onPrimary,
            raised = false,
            onClick = props.onIncrease,
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    background: Color,
    content: Color,
    raised: Boolean,
    onClick: () -> Unit,
) {
    val x = BahrTheme.colors
    val shape = BahrTheme.shapes.full
    Box(
        modifier =
            Modifier
                .size(BahrSpacing.minTouch)
                .then(if (enabled && raised) Modifier.bahrShadow(BahrElevation.Level1, shape, x) else Modifier)
                .clip(shape)
                .background(if (enabled) background else x.track)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = if (enabled) content else x.onSurfaceDisabled)
    }
}
