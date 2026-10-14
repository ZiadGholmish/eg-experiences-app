package eg.bahr.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.auth.data.AuthRepository
import eg.bahr.feature.auth.data.ProfilePrompt
import eg.bahr.feature.auth.data.ProviderResult
import eg.bahr.feature.auth.data.newRawNonce
import eg.bahr.feature.auth.data.sha256Hex
import eg.bahr.feature.auth.model.AuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A provider sign-in the screen must run now (the provider's UI needs the screen: Android's Activity,
 * iOS's window). [rawNonce] goes to our backend, [hashedNonce] to the provider (openapi
 * `signInWithProvider`).
 */
internal data class ProviderRequest(
    val provider: AuthProvider,
    val rawNonce: String,
    val hashedNonce: String,
)

internal data class SignInUiState(
    val step: Step = Step.Choose,
    val request: ProviderRequest? = null,
    /** The provider whose button shows a spinner: its sheet is up, or our backend is being asked. */
    val busy: AuthProvider? = null,
    val failed: Boolean = false,
    val error: AppError? = null,
) {
    enum class Step { Choose, Done }
}

/**
 * Sign-in with Apple or Google (M3-M0), for the sign-in sheet and, since M3-M0b, the buttons on Profile
 * and the Alerts tab: the provider's own UI, then our backend. A first sign-in (`profileComplete` false)
 * is done here too: the repository then asks for the name and phone in the app-wide sheet (M3-M0b,
 * handoff §2), over whatever screen is showing.
 *
 * Branches on the error code only. A cancelled provider sheet is no error.
 */
internal class SignInViewModel(
    private val repository: AuthRepository,
    private val nonces: () -> String = ::newRawNonce,
    private val hash: (String) -> String = ::sha256Hex,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    init {
        // The buttons on Profile and the Alerts tab keep this view model for as long as their tab: after a
        // sign-out (or a deleted account) they must offer sign-in again, so a finished sign-in starts over.
        repository.session
            .onEach { session ->
                if (session is SessionState.SignedOut && _uiState.value.step == SignInUiState.Step.Done) {
                    _uiState.value = SignInUiState()
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Opened while already signed in (a second entry point raced the first): finish, and ask for the
     * details still missing.
     */
    private fun initialState(): SignInUiState =
        when (val session = repository.session.value) {
            is SessionState.SignedIn -> {
                if (!session.profileComplete) {
                    repository.askForProfile(
                        ProfilePrompt(session.accountName.orEmpty(), phone = "", provider = session.providers.firstOrNull()),
                    )
                }
                SignInUiState(step = SignInUiState.Step.Done)
            }
            else -> SignInUiState()
        }

    fun start(provider: AuthProvider) {
        _uiState.update { state ->
            if (state.busy != null || state.step != SignInUiState.Step.Choose) return@update state
            val raw = nonces()
            state.copy(request = ProviderRequest(provider, raw, hash(raw)), busy = provider, failed = false, error = null)
        }
    }

    /** What the provider's UI gave back for [request]; a stale answer (another request since) is ignored. */
    fun onProviderResult(
        request: ProviderRequest,
        result: ProviderResult,
    ) {
        if (_uiState.value.request != request) return
        _uiState.update { it.copy(request = null) }
        when (result) {
            ProviderResult.Cancelled -> _uiState.update { it.copy(busy = null) }
            is ProviderResult.Failed -> _uiState.update { it.copy(busy = null, failed = true) }
            is ProviderResult.Success -> exchange(request, result)
        }
    }

    private fun exchange(
        request: ProviderRequest,
        result: ProviderResult.Success,
    ) {
        viewModelScope.launch {
            when (val signIn = repository.signIn(request.provider, result.idToken, request.rawNonce, result.fullName)) {
                // An incomplete profile is asked for by the repository's sheet, not here.
                is AppResult.Success -> _uiState.update { it.copy(busy = null, step = SignInUiState.Step.Done) }
                is AppResult.Failure -> _uiState.update { it.copy(busy = null, error = signIn.error) }
            }
        }
    }
}
