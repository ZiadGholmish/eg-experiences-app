package eg.bahr.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.theme.BahrBorder
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.auth_delete_body
import eg.bahr.core.localization.generated.resources.auth_delete_cancel
import eg.bahr.core.localization.generated.resources.auth_delete_confirm
import eg.bahr.core.localization.generated.resources.auth_delete_host
import eg.bahr.core.localization.generated.resources.auth_delete_title
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.auth.data.rememberProviderSignIn
import eg.bahr.feature.auth.model.AuthProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Delete your account?" (M3-M0; Apple requires account deletion in the app). A dialog destination, so
 * Settings only asks for it and the Apple re-authentication stays in this feature. Not coral: deleting
 * is not the screen's step forward, so the action is drawn in `error`.
 */
@Composable
internal fun DeleteAccountDialog(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: DeleteAccountViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val providerSignIn = rememberProviderSignIn()
    RunsProviderRequest(state.request, providerSignIn, viewModel::onProviderResult)

    val done by rememberUpdatedState(onDone)
    LaunchedEffect(state.done) { if (state.done) done() }

    val appleAvailable = AuthProvider.Apple in providerSignIn.providers
    DeleteAccountContent(
        state = state,
        onConfirm = { viewModel.confirm(appleAvailable) },
        onCancel = onCancel,
    )
}

/** Stateless, for the screenshot tests. */
@Composable
internal fun DeleteAccountContent(
    state: DeleteAccountUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.extraLarge)
                .background(c.surface)
                .padding(BahrSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Text(
            text = stringResource(Res.string.auth_delete_title),
            style = MaterialTheme.typography.titleLarge,
            color = c.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(Res.string.auth_delete_body), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        state.error?.let { error ->
            Text(text = deleteErrorText(error), style = MaterialTheme.typography.bodyMedium, color = c.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !state.deleting, modifier = Modifier.heightIn(min = BahrSpacing.minTouch)) {
                Text(stringResource(Res.string.auth_delete_cancel), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = onConfirm,
                enabled = !state.deleting,
                modifier = Modifier.heightIn(min = BahrSpacing.minTouch).testTag(DELETE_CONFIRM_TAG),
            ) {
                if (state.deleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(BahrSize.iconSmall),
                        color = c.error,
                        strokeWidth = BahrBorder.selected,
                    )
                } else {
                    Text(stringResource(Res.string.auth_delete_confirm), style = MaterialTheme.typography.labelLarge, color = c.error)
                }
            }
        }
    }
}

/** CONFLICT: the account is also a host's sign-in, removed through the back-office (openapi `deleteMe`). */
@Composable
private fun deleteErrorText(error: AppError): String =
    if ((error as? AppError.Api)?.code == ApiErrorCodes.CONFLICT) {
        stringResource(Res.string.auth_delete_host)
    } else {
        error.localizedMessage()
    }

internal const val DELETE_CONFIRM_TAG = "delete_account_confirm"
