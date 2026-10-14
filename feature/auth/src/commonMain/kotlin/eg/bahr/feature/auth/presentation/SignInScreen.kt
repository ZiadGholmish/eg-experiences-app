package eg.bahr.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.BahrSignInButton
import eg.bahr.core.designsystem.components.BahrTextFields
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.auth_body
import eg.bahr.core.localization.generated.resources.auth_close
import eg.bahr.core.localization.generated.resources.auth_continue_apple
import eg.bahr.core.localization.generated.resources.auth_continue_google
import eg.bahr.core.localization.generated.resources.auth_failed
import eg.bahr.core.localization.generated.resources.auth_privacy_note
import eg.bahr.core.localization.generated.resources.auth_profile_body
import eg.bahr.core.localization.generated.resources.auth_profile_later
import eg.bahr.core.localization.generated.resources.auth_profile_name
import eg.bahr.core.localization.generated.resources.auth_profile_phone
import eg.bahr.core.localization.generated.resources.auth_profile_phone_invalid
import eg.bahr.core.localization.generated.resources.auth_profile_save
import eg.bahr.core.localization.generated.resources.auth_profile_title
import eg.bahr.core.localization.generated.resources.auth_title
import eg.bahr.core.localization.generated.resources.auth_unavailable
import eg.bahr.core.localization.localizedMessage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.auth.data.ProviderSignIn
import eg.bahr.feature.auth.data.rememberProviderSignIn
import eg.bahr.feature.auth.model.AuthProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The sign-in sheet (M3-M0), opened only when needed: from My trips and Settings now, from checkout in
 * M3-M1. The handoff has no sign-in screen; the layout is this step's (see the step report): a close
 * button, a title and why, the provider buttons (Apple first where it is offered), a privacy line; then, on a first sign-in, the name and phone.
 *
 * [onDone] when signed in (and the profile saved or left for later); [onClose] to leave signed out.
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
        actions =
            SignInActions(
                onClose = onClose,
                onProvider = viewModel::start,
                onName = viewModel::setName,
                onPhone = viewModel::setPhone,
                onSave = viewModel::saveProfile,
                onLater = viewModel::skipProfile,
            ),
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
    onResult: (ProviderRequest, eg.bahr.feature.auth.data.ProviderResult) -> Unit,
) {
    val report by rememberUpdatedState(onResult)
    LaunchedEffect(request) {
        request?.let { report(it, providerSignIn.signIn(it.provider, it.hashedNonce)) }
    }
}

internal class SignInActions(
    val onClose: () -> Unit = {},
    val onProvider: (AuthProvider) -> Unit = {},
    val onName: (String) -> Unit = {},
    val onPhone: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onLater: () -> Unit = {},
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        IconButton(onClick = actions.onClose, modifier = Modifier.size(BahrSpacing.minTouch)) {
            Icon(BahrIcons.Close.outlined(), contentDescription = stringResource(Res.string.auth_close))
        }
        when (state.step) {
            SignInUiState.Step.Profile -> ProfileStep(state, actions)
            // Done shows the provider step until the screen is closed a frame later.
            SignInUiState.Step.Choose, SignInUiState.Step.Done -> ChooseStep(state, providers, actions)
        }
    }
}

@Composable
private fun ColumnScope.ChooseStep(
    state: SignInUiState,
    providers: List<AuthProvider>,
    actions: SignInActions,
) {
    Title(stringResource(Res.string.auth_title))
    Body(stringResource(Res.string.auth_body))
    Spacer(Modifier.height(BahrSpacing.xl))
    if (providers.isEmpty()) {
        Body(stringResource(Res.string.auth_unavailable))
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
                    onClick = { actions.onProvider(provider) },
                ),
        )
    }
    val problem =
        when {
            state.failed -> stringResource(Res.string.auth_failed)
            state.error != null -> signInErrorText(state.error)
            else -> null
        }
    problem?.let { ErrorLine(it) }
    Text(
        text = stringResource(Res.string.auth_privacy_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** UNAUTHORIZED here means the provider's token was refused: "couldn't sign you in", not "signed out". */
@Composable
private fun signInErrorText(error: AppError): String =
    if ((error as? AppError.Api)?.code == ApiErrorCodes.UNAUTHORIZED) {
        stringResource(Res.string.auth_failed)
    } else {
        error.localizedMessage()
    }

@Composable
private fun ColumnScope.ProfileStep(
    state: SignInUiState,
    actions: SignInActions,
) {
    Title(stringResource(Res.string.auth_profile_title))
    Body(stringResource(Res.string.auth_profile_body))
    OutlinedTextField(
        value = state.name,
        onValueChange = actions.onName,
        label = { Text(stringResource(Res.string.auth_profile_name)) },
        singleLine = true,
        shape = BahrTheme.shapes.medium,
        colors = BahrTextFields.colors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag(PROFILE_NAME_TAG),
    )
    var phoneFocused by remember { mutableStateOf(false) }
    val phoneError = state.phone.isNotEmpty() && !state.isPhoneValid && !phoneFocused
    OutlinedTextField(
        value = state.phone,
        onValueChange = actions.onPhone,
        label = { Text(stringResource(Res.string.auth_profile_phone)) },
        singleLine = true,
        isError = phoneError,
        supportingText = if (phoneError) ({ Text(stringResource(Res.string.auth_profile_phone_invalid)) }) else null,
        // Phone numbers stay left to right inside Arabic, like times.
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        shape = BahrTheme.shapes.medium,
        colors = BahrTextFields.colors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
        modifier = Modifier.fillMaxWidth().testTag(PROFILE_PHONE_TAG).onFocusChanged { phoneFocused = it.isFocused },
    )
    state.profileError?.let { ErrorLine(it.localizedMessage()) }
    Spacer(Modifier.height(BahrSpacing.lg))
    BahrPrimaryButton(
        text = stringResource(Res.string.auth_profile_save),
        onClick = actions.onSave,
        enabled = state.canSave,
        loading = state.saving,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = actions.onLater,
        enabled = !state.saving,
        modifier = Modifier.fillMaxWidth().heightIn(min = BahrSpacing.minTouch),
    ) {
        Text(stringResource(Res.string.auth_profile_later), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = BahrTheme.colors.onSurfaceSecondary)
}

@Composable
private fun ErrorLine(text: String) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            BahrIcons.Info.filled(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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

internal const val PROFILE_NAME_TAG = "profile_name"
internal const val PROFILE_PHONE_TAG = "profile_phone"
