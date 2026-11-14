package eg.bahr.feature.hostworkspace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.feature.hostworkspace.data.HostSignInOutcome
import eg.bahr.feature.hostworkspace.data.HostWorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two steps of a host's sign-in (M5-B0): the email and password Bahr gave, then a new password when one is due. */
internal enum class HostSignInStep { Credentials, ChangePassword }

/** What went wrong, as the form says it; never the server's message. */
internal enum class HostSignInProblem {
    /** UNAUTHORIZED on sign-in: unknown email, wrong password, locked or disabled login, not a host. One message for all. */
    Refused,

    /** UNAUTHORIZED on the change: the change token expired or was used; sign in again with the one-time password. */
    ChangeExpired,

    /** VALIDATION_FAILED on the change: outside 12–128 characters, or the old password again. */
    PasswordRejected,

    RateLimited,
}

internal data class HostSignInUiState(
    val step: HostSignInStep = HostSignInStep.Credentials,
    val email: String = "",
    val password: String = "",
    val passwordShown: Boolean = false,
    val newPassword: String = "",
    val confirmPassword: String = "",
    /** The field errors show once a submit was tried, not while the host is still typing. */
    val checked: Boolean = false,
    val busy: Boolean = false,
    val problem: HostSignInProblem? = null,
    /** Any other failure (no network, a server error): the app's own message for it. */
    val error: AppError? = null,
) {
    val emailMissing: Boolean get() = email.isBlank()
    val passwordMissing: Boolean get() = password.isEmpty()
    val newPasswordTooShort: Boolean get() = newPassword.length < MIN_PASSWORD
    val newPasswordTooLong: Boolean get() = newPassword.length > MAX_PASSWORD
    val confirmMismatch: Boolean get() = confirmPassword != newPassword

    companion object {
        /** openapi `PasswordChangeRequest.newPassword`: 12 to 128 characters, no other rules. */
        const val MIN_PASSWORD = 12
        const val MAX_PASSWORD = 128
    }
}

/**
 * A host's sign-in (M5-M1): `signInWithPassword` in body mode, then, for a new or reset login, `changePassword` with
 * the change token, which stays in this view model's memory only (never in the UI state, never stored). The tokens go
 * to host mode's own secure item; the traveller's sign-in is not touched. The passwords leave the state as soon as
 * they are no longer needed.
 */
internal class HostSignInViewModel(
    private val repository: HostWorkspaceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HostSignInUiState())
    val uiState: StateFlow<HostSignInUiState> = _uiState.asStateFlow()

    private var changeToken: String? = null

    fun onEmail(value: String) = _uiState.update { it.copy(email = value, problem = null, error = null) }

    fun onPassword(value: String) = _uiState.update { it.copy(password = value, problem = null, error = null) }

    fun togglePasswordShown() = _uiState.update { it.copy(passwordShown = !it.passwordShown) }

    fun onNewPassword(value: String) = _uiState.update { it.copy(newPassword = value, problem = null, error = null) }

    fun onConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value, problem = null, error = null) }

    fun signIn() {
        val state = _uiState.value
        if (state.busy) return
        _uiState.update { it.copy(checked = true) }
        if (state.emailMissing || state.passwordMissing) return
        _uiState.update { it.copy(busy = true, problem = null, error = null) }
        viewModelScope.launch {
            when (val result = repository.signIn(state.email, state.password)) {
                is AppResult.Success ->
                    when (val outcome = result.data) {
                        HostSignInOutcome.SignedIn -> _uiState.value = HostSignInUiState(email = state.email.trim())
                        is HostSignInOutcome.PasswordChangeRequired -> {
                            changeToken = outcome.changeToken
                            _uiState.update {
                                it.copy(
                                    step = HostSignInStep.ChangePassword,
                                    password = "",
                                    busy = false,
                                    checked = false,
                                    passwordShown = false,
                                )
                            }
                        }
                    }
                // The password is cleared for the next try; its "enter your password" waits for that try, not this refusal.
                is AppResult.Failure ->
                    _uiState.update {
                        it
                            .copy(
                                busy = false,
                                password = "",
                                checked = false,
                            ).failed(result.error, changing = false)
                    }
            }
        }
    }

    fun changePassword() {
        val state = _uiState.value
        if (state.busy) return
        _uiState.update { it.copy(checked = true) }
        if (state.newPasswordTooShort || state.newPasswordTooLong || state.confirmMismatch) return
        val token = changeToken ?: return backToCredentials()
        _uiState.update { it.copy(busy = true, problem = null, error = null) }
        viewModelScope.launch {
            when (val result = repository.changePassword(token, state.newPassword)) {
                is AppResult.Success -> {
                    changeToken = null
                    _uiState.value = HostSignInUiState(email = state.email.trim())
                }
                is AppResult.Failure -> {
                    val expired = (result.error as? AppError.Api)?.code == ApiErrorCodes.UNAUTHORIZED
                    if (expired) {
                        // The token works once and expires: only a new sign-in with the one-time password gets another.
                        changeToken = null
                        _uiState.update {
                            it.copy(
                                step = HostSignInStep.Credentials,
                                newPassword = "",
                                confirmPassword = "",
                                busy = false,
                                checked = false,
                                problem = HostSignInProblem.ChangeExpired,
                                error = null,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(busy = false).failed(result.error, changing = true) }
                    }
                }
            }
        }
    }

    /** Leaves the change step (a wrong account, an expired token): back to the email and password. */
    fun backToCredentials() {
        changeToken = null
        _uiState.update {
            it.copy(step = HostSignInStep.Credentials, newPassword = "", confirmPassword = "", checked = false, busy = false)
        }
    }

    private fun HostSignInUiState.failed(
        error: AppError,
        changing: Boolean,
    ): HostSignInUiState {
        val code = (error as? AppError.Api)?.code
        val problem =
            when {
                code == ApiErrorCodes.RATE_LIMITED -> HostSignInProblem.RateLimited
                code == ApiErrorCodes.UNAUTHORIZED && !changing -> HostSignInProblem.Refused
                code == ApiErrorCodes.VALIDATION_FAILED && changing -> HostSignInProblem.PasswordRejected
                else -> null
            }
        return copy(problem = problem, error = if (problem == null) error else null)
    }
}
