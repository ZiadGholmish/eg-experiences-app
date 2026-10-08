package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.booking_confirmed
import eg.bahr.core.localization.generated.resources.booking_your_ref
import eg.bahr.core.localization.generated.resources.trip_meeting_point
import eg.bahr.core.localization.generated.resources.trips_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BookingConfirmedScreen(
    ref: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingConfirmedViewModel = koinViewModel { parametersOf(ref) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val booking = state.booking

    when {
        state.isLoading -> BahrLoadingView(modifier)

        booking == null ->
            BahrErrorView(
                message = state.error?.localizedMessage().orEmpty(),
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = if (state.error?.isRetryable == true) viewModel::load else null,
                modifier = modifier,
            )

        else ->
            Column(
                modifier = modifier.fillMaxSize().padding(BahrSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(BahrSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BahrBadge(props = BahrBadge.Props(text = booking.status, tone = BahrBadge.Tone.Success))
                Text(
                    text = stringResource(Res.string.booking_confirmed),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(Res.string.booking_your_ref),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The reference is the one thing the user is asked to keep, so
                // it is the largest thing on the screen.
                Text(text = booking.ref, style = BahrTheme.type.display)

                booking.tripTitle?.let {
                    Text(text = it, style = MaterialTheme.typography.titleMedium)
                }
                booking.meetingPoint?.let { point ->
                    Text(
                        text = "${stringResource(Res.string.trip_meeting_point)}: $point",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                BahrPrimaryButton(
                    text = stringResource(Res.string.trips_title),
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
    }
}
