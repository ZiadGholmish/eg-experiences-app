package eg.bahr.feature.trips.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eg.bahr.core.designsystem.components.BahrBackButton
import eg.bahr.core.designsystem.theme.BahrSpacing

/**
 * The app bar of a list opened from Home: a round back button. The list's title sits in the larger
 * heading under it. Carries the status-bar inset itself (edge-to-edge; each screen handles its own
 * top inset).
 */
@Composable
internal fun ListTopBar(
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
