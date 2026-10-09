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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One message for [BahrErrorHost]: the localized text, and an [id] that tells two equal messages
 * apart (the same error twice is shown twice).
 */
@Immutable
data class BahrErrorMessage(
    val id: Long,
    val text: StringResource,
)

/**
 * The app-wide host for transient errors: mount it once, inside `BahrTheme` (so it is themed and
 * mirrored), around the navigation host. It shows [message] as a snackbar and calls [onDismissed]
 * once the snackbar has left the screen on its own; the caller then passes the next message.
 *
 * [onDismissed] is *not* called when the host is torn down while showing — a language switch
 * re-keys the composition below `ProvideAppLanguage`, and an Activity recreation rebuilds it. The
 * caller keeps the message, so the new host shows it again, now in the current language, with a
 * fresh display duration. That is the point: the message survives both.
 *
 * It takes localized *resources*, not text and not `AppError`: the design system knows no
 * business or result types, and the caller (the app root) maps its errors with
 * `AppError.messageRes()` from `core:localization`. The text is resolved inside the snackbar's
 * composition, so it is always in the current app language.
 */
@Composable
fun BahrErrorHost(
    message: BahrErrorMessage?,
    onDismissed: (BahrErrorMessage) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hostState = remember { SnackbarHostState() }
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    LaunchedEffect(message, hostState) {
        val shown = message ?: return@LaunchedEffect
        // Suspends until the snackbar times out; throws CancellationException instead when this
        // host leaves the composition, which skips the acknowledgement on purpose.
        hostState.showSnackbar(ErrorVisuals(shown.text))
        currentOnDismissed(shown)
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
