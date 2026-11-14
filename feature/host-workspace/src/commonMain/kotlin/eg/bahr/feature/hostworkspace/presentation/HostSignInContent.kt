package eg.bahr.feature.hostworkspace.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import eg.bahr.core.designsystem.components.BahrFormField
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.StickyActionBar
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.host_change_body
import eg.bahr.core.localization.generated.resources.host_change_confirm
import eg.bahr.core.localization.generated.resources.host_change_expired
import eg.bahr.core.localization.generated.resources.host_change_mismatch
import eg.bahr.core.localization.generated.resources.host_change_new
import eg.bahr.core.localization.generated.resources.host_change_other_account
import eg.bahr.core.localization.generated.resources.host_change_rejected
import eg.bahr.core.localization.generated.resources.host_change_rule
import eg.bahr.core.localization.generated.resources.host_change_save
import eg.bahr.core.localization.generated.resources.host_change_title
import eg.bahr.core.localization.generated.resources.host_change_too_long
import eg.bahr.core.localization.generated.resources.host_mode_title
import eg.bahr.core.localization.generated.resources.host_sign_in_body
import eg.bahr.core.localization.generated.resources.host_sign_in_email
import eg.bahr.core.localization.generated.resources.host_sign_in_email_missing
import eg.bahr.core.localization.generated.resources.host_sign_in_password
import eg.bahr.core.localization.generated.resources.host_sign_in_password_missing
import eg.bahr.core.localization.generated.resources.host_sign_in_rate_limited
import eg.bahr.core.localization.generated.resources.host_sign_in_refused
import eg.bahr.core.localization.generated.resources.host_sign_in_submit
import eg.bahr.core.localization.generated.resources.host_sign_in_title
import eg.bahr.core.localization.localizedMessage
import eg.bahr.feature.hostworkspace.presentation.components.HostTopBar
import org.jetbrains.compose.resources.stringResource

internal class HostSignInActions(
    val onExit: () -> Unit = {},
    val onEmail: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onTogglePassword: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onNewPassword: (String) -> Unit = {},
    val onConfirmPassword: (String) -> Unit = {},
    val onChangePassword: () -> Unit = {},
    val onOtherAccount: () -> Unit = {},
)

/**
 * A host's sign-in (M5-M1): the email and password Bahr gave them, then a new password when the login is new or was
 * reset (openapi `signInWithPassword` → `PASSWORD_CHANGE_REQUIRED` → `changePassword`). Laid out as Edit personal info
 * (top bar, a scrolling form, the coral step in the sticky bar); the design has no host sign-in of its own. Stateless,
 * for the screenshot tests.
 */
@Composable
internal fun HostSignInContent(
    state: HostSignInUiState,
    actions: HostSignInActions,
) {
    val changing = state.step == HostSignInStep.ChangePassword
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
    ) {
        HostTopBar(title = stringResource(Res.string.host_mode_title), onBack = actions.onExit)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = BahrSpacing.gutter, end = BahrSpacing.gutter, bottom = BahrSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BahrSpacing.gutter),
        ) {
            Intro(changing)
            if (changing) ChangeFields(state, actions) else CredentialFields(state, actions)
            Problem(state)
            if (changing) {
                TextButton(onClick = actions.onOtherAccount, enabled = !state.busy, modifier = Modifier.testTag(HOST_OTHER_ACCOUNT_TAG)) {
                    Text(
                        text = stringResource(Res.string.host_change_other_account),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        StickyActionBar {
            BahrPrimaryButton(
                text = stringResource(if (changing) Res.string.host_change_save else Res.string.host_sign_in_submit),
                onClick = if (changing) actions.onChangePassword else actions.onSignIn,
                loading = state.busy,
                modifier = Modifier.weight(1f).testTag(HOST_SIGN_IN_SUBMIT_TAG),
            )
        }
    }
}

@Composable
private fun Intro(changing: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(BahrSize.stateDisc)
                    .clip(BahrTheme.shapes.full)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                (if (changing) BahrIcons.Lock else BahrIcons.LockPerson).filled(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(BahrSize.iconState),
            )
        }
        Text(
            text = stringResource(if (changing) Res.string.host_change_title else Res.string.host_sign_in_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(if (changing) Res.string.host_change_body else Res.string.host_sign_in_body),
            style = MaterialTheme.typography.bodyLarge,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
    }
}

@Composable
private fun CredentialFields(
    state: HostSignInUiState,
    actions: HostSignInActions,
) {
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.host_sign_in_email),
                value = state.email,
                onValueChange = actions.onEmail,
                enabled = !state.busy,
                error = if (state.checked && state.emailMissing) stringResource(Res.string.host_sign_in_email_missing) else null,
                // An address reads left to right inside Arabic too.
                leftToRight = true,
                keyboard = BahrFormField.Keyboard.Email,
                testTag = HOST_EMAIL_TAG,
            ),
    )
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.host_sign_in_password),
                value = state.password,
                onValueChange = actions.onPassword,
                enabled = !state.busy,
                error = if (state.checked && state.passwordMissing) stringResource(Res.string.host_sign_in_password_missing) else null,
                leftToRight = true,
                keyboard = BahrFormField.Keyboard.Password,
                imeAction = ImeAction.Done,
                onImeAction = actions.onSignIn,
                concealed = !state.passwordShown,
                onToggleConcealed = actions.onTogglePassword,
                testTag = HOST_PASSWORD_TAG,
            ),
    )
}

@Composable
private fun ChangeFields(
    state: HostSignInUiState,
    actions: HostSignInActions,
) {
    val newError =
        when {
            !state.checked -> null
            state.newPasswordTooShort -> stringResource(Res.string.host_change_rule)
            state.newPasswordTooLong -> stringResource(Res.string.host_change_too_long)
            else -> null
        }
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.host_change_new),
                value = state.newPassword,
                onValueChange = actions.onNewPassword,
                enabled = !state.busy,
                support = stringResource(Res.string.host_change_rule),
                supportIsHelp = true,
                error = newError,
                leftToRight = true,
                keyboard = BahrFormField.Keyboard.Password,
                concealed = !state.passwordShown,
                onToggleConcealed = actions.onTogglePassword,
                testTag = HOST_NEW_PASSWORD_TAG,
            ),
    )
    BahrFormField(
        props =
            BahrFormField.Props(
                label = stringResource(Res.string.host_change_confirm),
                value = state.confirmPassword,
                onValueChange = actions.onConfirmPassword,
                enabled = !state.busy,
                error =
                    if (state.checked && !state.newPasswordTooShort &&
                        state.confirmMismatch
                    ) {
                        stringResource(Res.string.host_change_mismatch)
                    } else {
                        null
                    },
                leftToRight = true,
                keyboard = BahrFormField.Keyboard.Password,
                imeAction = ImeAction.Done,
                onImeAction = actions.onChangePassword,
                concealed = !state.passwordShown,
                testTag = HOST_CONFIRM_PASSWORD_TAG,
            ),
    )
}

@Composable
private fun Problem(state: HostSignInUiState) {
    val text =
        when (state.problem) {
            HostSignInProblem.Refused -> stringResource(Res.string.host_sign_in_refused)
            HostSignInProblem.ChangeExpired -> stringResource(Res.string.host_change_expired)
            HostSignInProblem.PasswordRejected -> stringResource(Res.string.host_change_rejected)
            HostSignInProblem.RateLimited -> stringResource(Res.string.host_sign_in_rate_limited)
            null -> state.error?.localizedMessage()
        } ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(BahrSpacing.md)
                .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

internal const val HOST_EMAIL_TAG = "host_sign_in_email"
internal const val HOST_PASSWORD_TAG = "host_sign_in_password"
internal const val HOST_NEW_PASSWORD_TAG = "host_change_new"
internal const val HOST_CONFIRM_PASSWORD_TAG = "host_change_confirm"
internal const val HOST_SIGN_IN_SUBMIT_TAG = "host_sign_in_submit"
internal const val HOST_OTHER_ACCOUNT_TAG = "host_change_other_account"
