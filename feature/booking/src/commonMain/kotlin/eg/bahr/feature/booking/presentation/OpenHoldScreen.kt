package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.components.BahrErrorView
import eg.bahr.core.designsystem.components.BahrLoadingView
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.action_retry
import eg.bahr.core.localization.generated.resources.booking_pay_step_title
import eg.bahr.core.localization.isRetryable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.components.BookingTopBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Between a tap on the hold reminder (M3-M7) and the held seats: the hold screen's own header over a loading view
 * while the hold is read again, or an error with a retry when it got no answer. [onOpen] gets the live hold, [onEnded]
 * is called when it is over (the app has said so).
 */
@Composable
internal fun OpenHoldScreen(
    ref: String,
    onOpen: (HoldRoute) -> Unit,
    onEnded: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenHoldViewModel = koinViewModel { parametersOf(ref) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpen)
    val ended by rememberUpdatedState(onEnded)
    LaunchedEffect(state.open, state.ended) {
        val route = state.open
        when {
            route != null -> {
                viewModel.onHandled()
                open(route)
            }
            state.ended -> {
                viewModel.onHandled()
                ended()
            }
        }
    }
    OpenHoldContent(state = state, onRetry = viewModel::load, onBack = onBack, modifier = modifier)
}

/** The screen without its view model, for the screenshot tests. */
@Composable
internal fun OpenHoldContent(
    state: OpenHoldUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        BookingTopBar(title = stringResource(Res.string.booking_pay_step_title), step = HOLD_STEP, onBack = onBack)
        val error = state.error
        if (error != null) {
            BahrErrorView(
                message = error.localizedMessage(),
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = if (error.isRetryable) onRetry else null,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            BahrLoadingView(modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** The held seats are step 2 of 3, as on the hold screen's header. */
private const val HOLD_STEP = 2
