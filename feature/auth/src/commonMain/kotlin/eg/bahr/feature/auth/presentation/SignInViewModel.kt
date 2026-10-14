package eg.bahr.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.phone.PhoneNumbers
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.auth.data.AuthRepository
import eg.bahr.feature.auth.data.ProviderResult
import eg.bahr.feature.auth.data.newRawNonce
import eg.bahr.feature.auth.data.sha256Hex
import eg.bahr.feature.auth.model.AuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val name: String = "",
    val phone: String = "",
    val saving: Boolean = false,
    val profileError: AppError? = null,
) {
    enum class Step { Choose, Profile, Done }

    val isNameValid: Boolean get() = name.isNotBlank() && name.trim().length <= NAME_MAX_LENGTH
    val isPhoneValid: Boolean get() = PhoneNumbers.isValid(phone)
    val canSave: Boolean get() = !saving && isNameValid && isPhoneValid

    companion object {
        /** openapi `UpdateAccountRequest.name` `maxLength`. */
        const val NAME_MAX_LENGTH = 80
    }
}

/**
 * The sign-in sheet (M3-M0): pick Apple or Google, the provider's own UI, then our backend; on a first
 * sign-in (`profileComplete` false) the name and phone, which may be left for later (checkout asks again).
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

    /** Opened while already signed in (a second entry point raced the first): finish, or finish the profile. */
    private fun initialState(): SignInUiState =
        when (val session = repository.session.value) {
            is SessionState.SignedIn ->
                if (session.profileComplete) {
                    SignInUiState(step = SignInUiState.Step.Done)
                } else {
                    SignInUiState(step = SignInUiState.Step.Profile, name = session.accountName.orEmpty())
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
                is AppResult.Success ->
                    _uiState.update { state ->
                        val account = signIn.data
                        if (account.profileComplete) {
                            state.copy(busy = null, step = SignInUiState.Step.Done)
                        } else {
                            state.copy(
                                busy = null,
                                step = SignInUiState.Step.Profile,
                                name = account.name.orEmpty(),
                                phone = account.phone.orEmpty(),
                            )
                        }
                    }
                is AppResult.Failure -> _uiState.update { it.copy(busy = null, error = signIn.error) }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name.take(SignInUiState.NAME_MAX_LENGTH), profileError = null) }

    fun setPhone(phone: String) = _uiState.update { it.copy(phone = PhoneNumbers.clean(phone), profileError = null) }

    fun saveProfile() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(saving = true, profileError = null) }
        viewModelScope.launch {
            when (val result = repository.updateProfile(state.name.trim(), state.phone)) {
                is AppResult.Success -> _uiState.update { it.copy(saving = false, step = SignInUiState.Step.Done) }
                is AppResult.Failure -> _uiState.update { it.copy(saving = false, profileError = result.error) }
            }
        }
    }

    /** "Later": signed in without the details; checkout asks for them again (profileComplete stays false). */
    fun skipProfile() = _uiState.update { it.copy(step = SignInUiState.Step.Done) }
}
