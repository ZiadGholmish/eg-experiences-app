package eg.bahr.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.auth_body
import eg.bahr.core.localization.generated.resources.auth_close
import eg.bahr.core.localization.generated.resources.auth_privacy_note
import eg.bahr.core.localization.generated.resources.auth_title
import eg.bahr.feature.auth.data.ProviderResult
import eg.bahr.feature.auth.data.ProviderSignIn
import eg.bahr.feature.auth.data.rememberProviderSignIn
import eg.bahr.feature.auth.model.AuthProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The sign-in sheet (M3-M0), opened only when needed: from My trips now, from checkout in M3-M1. The
 * handoff has no sign-in screen; the layout is M3-M0's: a close button, a title and why, the provider
 * buttons (Apple first where it is offered), a privacy line. Since M3-M0b the name and phone a first
 * sign-in still needs are asked in the app-wide sheet ([CompleteProfileSheet]) once this closes.
 *
 * [onDone] when signed in; [onClose] to leave signed out.
 */
@Composable
internal fun SignInScreen(
    onDone: () -> Unit,
    onClose: () -> Unit,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val providerSignIn = rememberProviderSignIn()
    RunsProviderRequest(state.request, providerSignIn, viewModel::onProviderResult)

    val done by rememberUpdatedState(onDone)
    LaunchedEffect(state.step) { if (state.step == SignInUiState.Step.Done) done() }

    SignInContent(
        state = state,
        providers = providerSignIn.providers,
        actions = SignInActions(onClose = onClose, onProvider = viewModel::start),
    )
}

/**
 * Runs the provider's own UI for [request] and reports the answer. Here and not in the view model: the
 * provider sheet needs the screen (Android's Activity, iOS's window), and leaving the screen cancels it.
 */
@Composable
internal fun RunsProviderRequest(
    request: ProviderRequest?,
    providerSignIn: ProviderSignIn,
    onResult: (ProviderRequest, ProviderResult) -> Unit,
) {
    val report by rememberUpdatedState(onResult)
    LaunchedEffect(request) {
        request?.let { report(it, providerSignIn.signIn(it.provider, it.hashedNonce)) }
    }
}

internal class SignInActions(
    val onClose: () -> Unit = {},
    val onProvider: (AuthProvider) -> Unit = {},
)

/** Stateless, for the screenshot tests. */
@Composable
internal fun SignInContent(
    state: SignInUiState,
    providers: List<AuthProvider>,
    actions: SignInActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        IconButton(onClick = actions.onClose, modifier = Modifier.size(BahrSize.touchTarget)) {
            Icon(BahrIcons.Close.outlined(), contentDescription = stringResource(Res.string.auth_close))
        }
        Text(
            text = stringResource(Res.string.auth_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.auth_body),
            style = MaterialTheme.typography.bodyLarge,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
        Spacer(Modifier.height(BahrSpacing.xl))
        SignInButtonsContent(state = state, providers = providers, onProvider = actions.onProvider)
        Text(
            text = stringResource(Res.string.auth_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
