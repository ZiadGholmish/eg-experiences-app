package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.component.EgBadge
import eg.bahr.core.designsystem.component.EgErrorView
import eg.bahr.core.designsystem.component.EgLoadingView
import eg.bahr.core.designsystem.component.EgPrimaryButton
import eg.bahr.core.designsystem.theme.EgInkSubtle
import eg.bahr.core.designsystem.theme.EgSuccess
import eg.bahr.core.designsystem.theme.EgSuccessTint
import eg.bahr.core.designsystem.theme.EgTheme
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
        state.isLoading -> EgLoadingView(modifier)

        booking == null ->
            EgErrorView(
                message = state.error?.localizedMessage().orEmpty(),
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = if (state.error?.isRetryable == true) viewModel::load else null,
                modifier = modifier,
            )

        else ->
            Column(
                modifier = modifier.fillMaxSize().padding(EgTheme.spacings.screenGutter),
                verticalArrangement = Arrangement.spacedBy(EgTheme.spacings.x4l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EgBadge(
                    text = booking.status,
                    container = EgSuccessTint,
                    content = EgSuccess,
                )
                Text(
                    text = stringResource(Res.string.booking_confirmed),
                    style = EgTheme.typography.screenTitle,
                )
                Text(
                    text = stringResource(Res.string.booking_your_ref),
                    style = EgTheme.typography.meta,
                    color = EgInkSubtle,
                )
                // The reference is the one thing the user is asked to keep, so
                // it is the largest thing on the screen.
                Text(text = booking.ref, style = EgTheme.typography.priceLarge)

                booking.tripTitle?.let {
                    Text(text = it, style = EgTheme.typography.cardTitle)
                }
                booking.meetingPoint?.let { point ->
                    Text(
                        text = "${stringResource(Res.string.trip_meeting_point)}: $point",
                        style = EgTheme.typography.body,
                        color = EgInkSubtle,
                    )
                }

                EgPrimaryButton(
                    text = stringResource(Res.string.trips_title),
                    onClick = onDone,
                )
            }
    }
}
