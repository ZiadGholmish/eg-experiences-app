package eg.bahr.feature.notifications.data

import eg.bahr.core.network.auth.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sign-in in memory; [loaded] what [loadSession] makes it (the store's answer). */
internal class FakeNotificationsRepository(
    private val loaded: SessionState = SessionState.SignedOut,
) : NotificationsRepository {
    val state = MutableStateFlow<SessionState>(SessionState.Unknown)
    override val session: StateFlow<SessionState> = state

    override suspend fun loadSession() {
        state.value = loaded
    }
}
