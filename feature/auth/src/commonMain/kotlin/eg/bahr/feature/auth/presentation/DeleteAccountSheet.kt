package eg.bahr.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrBottomSheet
import eg.bahr.core.designsystem.components.BahrBottomSheetContent
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.auth_delete_body
import eg.bahr.core.localization.generated.resources.auth_delete_cancel
import eg.bahr.core.localization.generated.resources.auth_delete_confirm
import eg.bahr.core.localization.generated.resources.auth_delete_gone
import eg.bahr.core.localization.generated.resources.auth_delete_host
import eg.bahr.core.localization.generated.resources.auth_delete_kept
import eg.bahr.core.localization.generated.resources.auth_delete_title
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.auth.data.rememberProviderSignIn
import eg.bahr.feature.auth.model.AuthProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Delete your account?" (M3-M0; Apple requires account deletion in the app), as a bottom sheet since
 * M3-M0b (user decision 2026-10-14: sheets instead of dialogs). Profile places it through
 * `DeleteAccountSheetSlot`, so the Apple re-authentication stays in this feature. [onDismiss] when kept,
 * swiped away, or once the account is deleted (the session is then signed out and Profile follows it).
 */
@Composable
internal fun DeleteAccountSheet(
    onDismiss: () -> Unit,
    viewModel: DeleteAccountViewModel = koinViewModel(),
) {
    // The view model lives as long as the screen that places the sheet: each opening starts afresh.
    LaunchedEffect(viewModel) { viewModel.reset() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val providerSignIn = rememberProviderSignIn()
    RunsProviderRequest(state.request, providerSignIn, viewModel::onProviderResult)

    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.done) { if (state.done) dismiss() }

    val appleAvailable = AuthProvider.Apple in providerSignIn.providers
    BahrBottomSheet(
        props = props(state, onConfirm = { viewModel.confirm(appleAvailable) }, onCancel = onDismiss),
    ) { DeleteAccountBody(state) }
}

/** The sheet's inside, for the screenshot tests (a sheet is a second window they cannot draw). */
@Composable
internal fun DeleteAccountContent(
    state: DeleteAccountUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    BahrBottomSheetContent(props(state, onConfirm, onCancel)) { DeleteAccountBody(state) }
}

/**
 * The handoff's delete confirm (§2): "Delete account" filled `errorStrong` (coral means go forward),
 * then "Keep account", which takes the default focus so a stray Enter keeps it.
 */
@Composable
private fun props(
    state: DeleteAccountUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) = BahrBottomSheet.Props(
    title = stringResource(Res.string.auth_delete_title),
    onDismiss = onCancel,
    actions =
        listOf(
            BahrBottomSheet.Action(
                text = stringResource(Res.string.auth_delete_confirm),
                style = BahrBottomSheet.Action.Style.Destructive,
                onClick = onConfirm,
                loading = state.deleting,
                testTag = DELETE_CONFIRM_TAG,
            ),
        ),
    cancel =
        BahrBottomSheet.Action(
            text = stringResource(Res.string.auth_delete_cancel),
            onClick = onCancel,
            enabled = !state.deleting,
            testTag = DELETE_CANCEL_TAG,
        ),
    // Stays up while deleting: it closes when done, or shows why not (review R4-2).
    dismissible = !state.deleting,
)

/** The `delete` disc, "This can't be undone.", what goes and what stays, and an error if there is one. */
@Composable
private fun ColumnScope.DeleteAccountBody(state: DeleteAccountUiState) {
    val c = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(BahrSize.touchTarget).clip(BahrTheme.shapes.full).background(c.errorContainer),
    ) {
        Icon(BahrIcons.Delete.outlined(), contentDescription = null, tint = c.onErrorContainer, modifier = Modifier.size(BahrSize.iconNav))
    }
    Text(text = stringResource(Res.string.auth_delete_body), style = MaterialTheme.typography.labelLarge, color = c.onErrorContainer)
    Fact(BahrIcons.PersonRemove, c.onErrorContainer, stringResource(Res.string.auth_delete_gone))
    Fact(BahrIcons.ConfirmationNumber, c.primary, stringResource(Res.string.auth_delete_kept))
    state.error?.let { error ->
        Text(
            text = deleteErrorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onErrorContainer,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

/** One line of what deleting does: an icon in its colour, then the sentence. */
@Composable
private fun Fact(
    icon: BahrIcons,
    tint: Color,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Icon(icon.outlined(), contentDescription = null, tint = tint, modifier = Modifier.size(BahrSize.iconMedium))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = BahrTheme.colors.onSurfaceSecondary)
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
internal const val DELETE_CANCEL_TAG = "delete_account_cancel"
