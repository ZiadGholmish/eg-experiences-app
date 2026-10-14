package eg.bahr.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrSignInButton
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.auth_continue_apple
import eg.bahr.core.localization.generated.resources.auth_continue_google
import eg.bahr.core.localization.generated.resources.auth_failed
import eg.bahr.core.localization.generated.resources.auth_unavailable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.auth.data.rememberProviderSignIn
import eg.bahr.feature.auth.model.AuthProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Continue with Apple" (iOS) and "Continue with Google", working where they are drawn (M3-M0b: Profile
 * and the Alerts tab signed out, handoff §2/§3), with no sheet in between. A sign-in that comes back
 * without a name or phone asks for them in the app-wide sheet ([CompleteProfileSheet]); the host
 * screen follows the session on its own.
 */
@Composable
internal fun SignInButtons(
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val providerSignIn = rememberProviderSignIn()
    RunsProviderRequest(state.request, providerSignIn, viewModel::onProviderResult)
    SignInButtonsContent(state, providerSignIn.providers, viewModel::start, modifier)
}

/** Stateless, for the screenshot tests and the sign-in sheet. */
@Composable
internal fun SignInButtonsContent(
    state: SignInUiState,
    providers: List<AuthProvider>,
    onProvider: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
        if (providers.isEmpty()) {
            Text(
                stringResource(Res.string.auth_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        providers.forEach { provider ->
            BahrSignInButton(
                modifier = Modifier.testTag(providerTag(provider)),
                props =
                    BahrSignInButton.Props(
                        provider = provider.button(),
                        text = stringResource(provider.label()),
                        loading = state.busy == provider,
                        enabled = state.busy == null,
                        onClick = { onProvider(provider) },
                    ),
            )
        }
        val problem =
            when {
                state.failed -> stringResource(Res.string.auth_failed)
                state.error != null -> signInErrorText(state.error)
                else -> null
            }
        problem?.let { SignInErrorLine(it) }
    }
}

/** UNAUTHORIZED here means the provider's token was refused: "couldn't sign you in", not "signed out". */
@Composable
private fun signInErrorText(error: AppError): String =
    if ((error as? AppError.Api)?.code == ApiErrorCodes.UNAUTHORIZED) {
        stringResource(Res.string.auth_failed)
    } else {
        error.localizedMessage()
    }

/** Error text in `onErrorContainer` (handoff: `error` is only 3.9:1 at this size), announced once. */
@Composable
private fun SignInErrorLine(text: String) {
    val color = MaterialTheme.colorScheme.onErrorContainer
    Row(
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(BahrIcons.Error.outlined(), contentDescription = null, tint = color, modifier = Modifier.size(BahrSize.iconMedium))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private fun AuthProvider.button(): BahrSignInButton.Provider =
    when (this) {
        AuthProvider.Apple -> BahrSignInButton.Provider.Apple
        AuthProvider.Google -> BahrSignInButton.Provider.Google
    }

private fun AuthProvider.label() =
    when (this) {
        AuthProvider.Apple -> Res.string.auth_continue_apple
        AuthProvider.Google -> Res.string.auth_continue_google
    }

/** For tests. */
internal fun providerTag(provider: AuthProvider): String = "sign_in_${provider.wire}"
