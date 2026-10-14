package eg.bahr.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.notifications.data.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class NotificationsUiState(
    val session: SessionState = SessionState.Unknown,
)

/**
 * The Alerts tab (M3-M0b). Signed out: the handoff's signed-out state (sign-in buttons); signed in: its
 * empty state, until M3-B9 / M3-M6 bring the list. Signing in or out anywhere reaches it through the
 * session.
 */
internal class NotificationsViewModel(
    private val repository: NotificationsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        repository.session
            .onEach { session -> _uiState.update { it.copy(session = session) } }
            .launchIn(viewModelScope)
        viewModelScope.launch { repository.loadSession() }
    }
}
