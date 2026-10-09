package eg.bahr.feature.booking.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_back
import eg.bahr.core.localization.generated.resources.booking_step_of
import org.jetbrains.compose.resources.stringResource

/**
 * The booking flow's app bar (HANDOFF screens 4–5): a round back button, the step's title, and the
 * three-segment step indicator. It carries the status-bar inset itself (edge-to-edge is on and each
 * screen handles its own top inset, M1-M1a review #10).
 *
 * [step] is 1-based, out of [BOOKING_STEPS].
 */
@Composable
internal fun BookingTopBar(
    title: String,
    step: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        BackButton(onBack)
        Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
        StepIndicator(step)
    }
}

/** The handoff draws it at 38px on `primaryContainer`; laid out at the 44dp touch minimum. The arrow mirrors in RTL. */
@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(BahrSpacing.minTouch)
                .clip(BahrTheme.shapes.full)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BahrIcons.ArrowBack.outlined(),
            contentDescription = stringResource(Res.string.action_back),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
    }
}

/** Done and current segments in `primary`, the rest in `track`; read out as "Step 1 of 3". */
@Composable
private fun StepIndicator(step: Int) {
    val description = stringResource(Res.string.booking_step_of, step, BOOKING_STEPS)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        repeat(BOOKING_STEPS) { index ->
            Box(
                Modifier
                    .size(width = BahrSize.stepSegmentWidth, height = BahrSize.stepSegmentHeight)
                    .clip(BahrTheme.shapes.full)
                    .background(if (index < step) MaterialTheme.colorScheme.primary else BahrTheme.colors.track),
            )
        }
    }
}

/** Date and party, payment, confirmation. */
internal const val BOOKING_STEPS = 3
