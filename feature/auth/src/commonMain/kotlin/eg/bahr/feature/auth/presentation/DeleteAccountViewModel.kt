package eg.bahr.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

internal data class DeleteAccountUiState(
    /** Apple's re-authentication the screen must run before deleting (openapi `deleteMe`). */
    val request: ProviderRequest? = null,
    val deleting: Boolean = false,
    val error: AppError? = null,
    val done: Boolean = false,
)

/**
 * "Delete account" (M3-M0; Apple requires it), after the person confirmed in the dialog.
 *
 * An account that signs in with Apple re-authenticates with Apple first, where this device can
 * ([appleAvailable], iOS), and sends the fresh authorization code so the server revokes the Apple tokens
 * (openapi `deleteMe`). If that sheet is cancelled nothing is deleted; if it fails, the account is still
 * deleted without the code (the contract allows it: the revocation is skipped and logged).
 */
internal class DeleteAccountViewModel(
    private val repository: AuthRepository,
    private val nonces: () -> String = ::newRawNonce,
    private val hash: (String) -> String = ::sha256Hex,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    fun confirm(appleAvailable: Boolean) {
        val state = _uiState.value
        if (state.deleting || state.done) return
        val usesApple = (repository.session.value as? SessionState.SignedIn)?.providers?.contains(AuthProvider.Apple.wire) == true
        if (usesApple && appleAvailable) {
            val raw = nonces()
            _uiState.update { it.copy(request = ProviderRequest(AuthProvider.Apple, raw, hash(raw)), deleting = true, error = null) }
        } else {
            delete(appleAuthorizationCode = null)
        }
    }

    fun onProviderResult(
        request: ProviderRequest,
        result: ProviderResult,
    ) {
        if (_uiState.value.request != request) return
        _uiState.update { it.copy(request = null) }
        when (result) {
            ProviderResult.Cancelled -> _uiState.update { it.copy(deleting = false) }
            is ProviderResult.Failed -> delete(appleAuthorizationCode = null)
            is ProviderResult.Success -> delete(result.authorizationCode)
        }
    }

    private fun delete(appleAuthorizationCode: String?) {
        _uiState.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteAccount(appleAuthorizationCode)) {
                is AppResult.Success -> _uiState.update { it.copy(deleting = false, done = true) }
                is AppResult.Failure -> _uiState.update { it.copy(deleting = false, error = result.error) }
            }
        }
    }
}
