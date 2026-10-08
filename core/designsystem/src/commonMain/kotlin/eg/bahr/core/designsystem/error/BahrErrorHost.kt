package eg.bahr.core.designsystem.error

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The app-wide host for transient errors: mount it once, inside `BahrTheme` (so it is themed and
 * mirrored), around the navigation host. Each message is shown as a snackbar, one at a time, in
 * arrival order.
 *
 * It takes localized *resources*, not text and not `AppError`: the design system knows no
 * business or result types, and the caller (the app root) maps its errors with
 * `AppError.messageRes()` from `core:localization`. The text is resolved inside the snackbar's
 * composition, so it is always in the current app language — also after a language switch,
 * which a string resolved up front in a coroutine would miss.
 */
@Composable
fun BahrErrorHost(
    messages: Flow<StringResource>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(messages, hostState) {
        // showSnackbar suspends until the current one is dismissed, so messages queue up.
        messages.collect { hostState.showSnackbar(ErrorVisuals(it)) }
    }
    Box(modifier.fillMaxSize()) {
        content()
        SnackbarHost(
            hostState = hostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(BahrSpacing.gutter),
        ) { data ->
            val visuals = data.visuals as ErrorVisuals
            BahrErrorSnackbar(visuals.resource)
        }
    }
}

/** The snackbar itself; split out so screenshot tests can render it without the queue. */
@Composable
internal fun BahrErrorSnackbar(
    message: StringResource,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier,
        shape = BahrTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * [SnackbarVisuals.message] must be a String, but the text is only known in composition; the
 * resource key stands in for it (it is never displayed).
 */
private class ErrorVisuals(
    val resource: StringResource,
) : SnackbarVisuals {
    override val message: String = resource.key
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}
