package eg.bahr.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.onFailure
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.settings.data.SettingsRepository
import eg.bahr.feature.settings.model.AccountDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [language] is the stored one, null until the store has answered (the choice is then drawn with
 * nothing picked rather than a guess that could flip).
 */
internal data class ProfileUiState(
    val language: AppLanguage? = null,
    /** Profile → Appearance; null until the store has answered. */
    val appearance: AppAppearance? = null,
    val session: SessionState = SessionState.Unknown,
    /** The account's details (`getMe`): email and phone are not in the session. Null until read. */
    val account: AccountDto? = null,
    val signingOut: Boolean = false,
    /** Edit personal info saved: show "Saved" once. */
    val showSaved: Boolean = false,
)

/**
 * Profile (M3-M0b; the Settings tab of M4-M8). Signed out: the sign-in buttons (feature:auth's), what
 * works without an account, Language and About. Signed in: who it is and the account's rows.
 *
 * Picking a language only writes it to the store: the app's Compose roots read the store and re-theme
 * (script, RTL/LTR, strings, `Accept-Language`) on their own. Signing in or out anywhere reaches this
 * screen through the session; a sign-in reads the account's details, a sign-out forgets them.
 */
internal class ProfileViewModel(
    private val repository: SettingsRepository,
    private val errors: AppErrorController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var accountRead: Job? = null

    init {
        repository.language
            .onEach { language -> _uiState.update { it.copy(language = language) } }
            .launchIn(viewModelScope)
        repository.appearance
            .onEach { appearance -> _uiState.update { it.copy(appearance = appearance) } }
            .launchIn(viewModelScope)
        repository.account
            .onEach { account -> _uiState.update { it.copy(account = account) } }
            .launchIn(viewModelScope)
        repository.savedNotice
            .onEach { saved -> _uiState.update { it.copy(showSaved = saved) } }
            .launchIn(viewModelScope)
        repository.session
            .onEach { session ->
                val before = _uiState.value.session
                _uiState.update { it.copy(session = session) }
                when {
                    session !is SessionState.SignedIn -> accountRead?.cancel()
                    // A new sign-in (or the first read after launch), or the account changed elsewhere (the
                    // first-sign-in sheet saved the name and phone, M3-M0b review #2): read who it is. A save
                    // from Edit personal info leaves a matching account behind, so nothing is read for it.
                    before !is SessionState.SignedIn -> readAccount()
                    before.summary() != session.summary() -> if (repository.account.first() == null) readAccount()
                }
            }.launchIn(viewModelScope)
        viewModelScope.launch { repository.loadSession() }
    }

    /**
     * Reads the account again (each visit, so a change made on another phone shows). A failure keeps
     * what is shown: the session's name stands in, and nothing else depends on it.
     */
    fun refresh() {
        if (_uiState.value.session is SessionState.SignedIn) readAccount()
    }

    private fun readAccount() {
        accountRead?.cancel()
        accountRead = viewModelScope.launch { repository.refreshAccount() }
    }

    /** Sign-out always succeeds on the device (the server is told when it can be reached). */
    fun signOut() {
        if (_uiState.value.signingOut || _uiState.value.session !is SessionState.SignedIn) return
        _uiState.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            repository.signOut()
            _uiState.update { it.copy(signingOut = false) }
        }
    }

    fun selectLanguage(language: AppLanguage) {
        // The language already shown: nothing to write, and no rebuild of every screen.
        if (language == _uiState.value.language) return
        viewModelScope.launch {
            repository.setLanguage(language).onFailure(errors::show)
        }
    }

    private fun SessionState.SignedIn.summary() = accountName to profileComplete

    fun selectAppearance(appearance: AppAppearance) {
        if (appearance == _uiState.value.appearance) return
        viewModelScope.launch {
            repository.setAppearance(appearance).onFailure(errors::show)
        }
    }

    /** The "Saved" snackbar has been shown. */
    fun savedShown() = repository.setSavedNotice(false)
}
