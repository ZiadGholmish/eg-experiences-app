package eg.bahr.feature.hostworkspace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.hostworkspace.data.HostRefusal
import eg.bahr.feature.hostworkspace.data.HostWorkspaceRepository
import eg.bahr.feature.hostworkspace.data.hostRefusal
import eg.bahr.feature.hostworkspace.model.HostDepartureSummaryDto
import eg.bahr.feature.hostworkspace.model.HostMeDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who the signed-in host is, as `getHostMe` says, or why host mode cannot open. */
internal sealed interface HostAccess {
    data object Loading : HostAccess

    data class Ready(
        val me: HostMeDto,
    ) : HostAccess

    /** FORBIDDEN, HOST_SUSPENDED: the suspended page, with a way out (sign out, back to the traveller app). */
    data object Suspended : HostAccess

    /** Any other FORBIDDEN: an account that runs no approved host (pending, rejected, or a customer). */
    data object NotActive : HostAccess

    data class Failed(
        val error: AppError,
    ) : HostAccess
}

/** The departure picker's list (`listHostDepartures`, from today in Cairo). */
internal sealed interface HostDepartures {
    data object Loading : HostDepartures

    data class Loaded(
        val items: List<HostDepartureSummaryDto>,
    ) : HostDepartures

    data class Failed(
        val error: AppError,
    ) : HostDepartures
}

internal data class HostHomeUiState(
    val session: SessionState = SessionState.Unknown,
    val access: HostAccess = HostAccess.Loading,
    val departures: HostDepartures = HostDepartures.Loading,
    val signingOut: Boolean = false,
)

/**
 * Host mode's shell (M5-M1): follows host mode's own session (never the traveller's). Signed out, the screen shows the
 * sign-in; signed in, it reads `getHostMe` and the host's dates. A 401 nothing could cure drops the host's tokens in
 * core:network, which lands here as [SessionState.SignedOut]: back to the sign-in, the traveller still signed in.
 */
internal class HostHomeViewModel(
    private val repository: HostWorkspaceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HostHomeUiState())
    val uiState: StateFlow<HostHomeUiState> = _uiState.asStateFlow()

    private var loading: Job? = null

    init {
        viewModelScope.launch {
            repository.loadSession()
            repository.session
                // A token refresh or a name update is still the same sign-in; only in/out matters here.
                .map { it.kind() }
                .distinctUntilChanged()
                .collect { kind ->
                    _uiState.update { HostHomeUiState(session = repository.session.value) }
                    if (kind == SessionKind.In) refresh()
                }
        }
    }

    /** Reads the host and the dates again (opening host mode, a return to it, "Try again"). */
    fun refresh() {
        if (repository.session.value !is SessionState.SignedIn) return
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val me = async { repository.me() }
                val dates = async { repository.departures() }
                val access =
                    when (val result = me.await()) {
                        is AppResult.Success -> HostAccess.Ready(result.data)
                        is AppResult.Failure -> result.error.toAccess()
                    }
                val departures =
                    when (val result = dates.await()) {
                        is AppResult.Success -> HostDepartures.Loaded(result.data)
                        is AppResult.Failure -> HostDepartures.Failed(result.error)
                    }
                _uiState.update { it.copy(access = access, departures = departures) }
            }
    }

    /** Ends host mode's sign-in only (the traveller's stays); the screen then shows the sign-in again. */
    fun signOut() {
        if (_uiState.value.signingOut) return
        _uiState.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            repository.signOut()
            _uiState.update { it.copy(signingOut = false) }
        }
    }

    private fun AppError.toAccess(): HostAccess =
        when (hostRefusal()) {
            HostRefusal.Suspended -> HostAccess.Suspended
            // getHostMe has no id to be foreign: a FORBIDDEN here is an account that runs no approved host.
            HostRefusal.NotAvailable ->
                if ((this as? AppError.Api)?.code ==
                    ApiErrorCodes.FORBIDDEN
                ) {
                    HostAccess.NotActive
                } else {
                    HostAccess.Failed(this)
                }
            HostRefusal.Other -> HostAccess.Failed(this)
        }

    private enum class SessionKind { Unknown, Out, In }

    private fun SessionState.kind(): SessionKind =
        when (this) {
            SessionState.Unknown -> SessionKind.Unknown
            SessionState.SignedOut -> SessionKind.Out
            is SessionState.SignedIn -> SessionKind.In
        }
}
