package eg.bahr.feature.notifications.data

import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Alerts tab reads (M3-M0b): whether someone is signed in. The notifications themselves
 * (`GET /me/notifications`, the unread count) arrive with M3-B9 / M3-M6.
 */
internal interface NotificationsRepository {
    val session: StateFlow<SessionState>

    /** Reads the stored sign-in once (it starts as Unknown). */
    suspend fun loadSession()
}

internal class DefaultNotificationsRepository(
    private val authSession: AuthSession,
) : NotificationsRepository {
    override val session: StateFlow<SessionState> = authSession.state

    override suspend fun loadSession() {
        authSession.load()
    }
}
