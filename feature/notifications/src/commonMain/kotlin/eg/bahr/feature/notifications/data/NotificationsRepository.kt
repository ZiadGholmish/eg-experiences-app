package eg.bahr.feature.notifications.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.ApiErrorReasons
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.notifications.model.NotificationDto
import kotlinx.coroutines.flow.StateFlow

/**
 * The inbox (M3-M6): the signed-in account's, or this install's guest inbox while signed out (M3-B12: the page works
 * signed out). Which one follows [session]; the caller names it ([InboxOwner]) so a sign-in mid-request never sends one
 * owner's action to the other's inbox.
 */
internal interface NotificationsRepository {
    val session: StateFlow<SessionState>

    /** Reads the stored sign-in once (it starts as Unknown). */
    suspend fun loadSession()

    suspend fun page(
        owner: InboxOwner,
        page: Int,
    ): AppResult<PageDto<NotificationDto>>

    /**
     * [registerOnRefusal]: a guest inbox that refuses the install's secret registers the install again first (the
     * contract's "register again"), unless this read follows a registration already (the bell after one).
     */
    suspend fun unreadCount(
        owner: InboxOwner,
        registerOnRefusal: Boolean = true,
    ): AppResult<Int>

    suspend fun mark(
        owner: InboxOwner,
        id: String,
        read: Boolean,
    ): AppResult<Unit>

    suspend fun markAllRead(owner: InboxOwner): AppResult<Unit>

    suspend fun delete(
        owner: InboxOwner,
        id: String,
    ): AppResult<Unit>

    suspend fun clearRead(owner: InboxOwner): AppResult<Unit>
}

/** The inbox of whoever [this] session is; null while the stored sign-in is still being read. */
internal val SessionState.inboxOwner: InboxOwner?
    get() =
        when (this) {
            SessionState.Unknown -> null
            SessionState.SignedOut -> InboxOwner.Guest
            is SessionState.SignedIn -> InboxOwner.Account
        }

/**
 * [register] is core:network's `InstallRegistrar.register`: a guest-inbox call refused with FORBIDDEN
 * `INSTALL_SECRET_INVALID` (openapi: missing or wrong secret, or an install the server does not know) registers the
 * install again and is sent **once** more. Never more: a second refusal is the answer (an install that still cannot
 * prove itself, e.g. secure storage that cannot be read, must not loop).
 */
internal class DefaultNotificationsRepository(
    private val api: NotificationsApiService,
    private val authSession: AuthSession,
    private val register: suspend () -> AppResult<Unit>,
) : NotificationsRepository {
    override val session: StateFlow<SessionState> = authSession.state

    override suspend fun loadSession() {
        authSession.load()
    }

    override suspend fun page(
        owner: InboxOwner,
        page: Int,
    ) = proven(owner) { api.page(owner, page) }

    override suspend fun unreadCount(
        owner: InboxOwner,
        registerOnRefusal: Boolean,
    ): AppResult<Int> {
        val result = if (registerOnRefusal) proven(owner) { api.unreadCount(owner) } else api.unreadCount(owner)
        return when (result) {
            is AppResult.Success -> AppResult.Success(result.data.count)
            is AppResult.Failure -> result
        }
    }

    override suspend fun mark(
        owner: InboxOwner,
        id: String,
        read: Boolean,
    ) = proven(owner) { api.mark(owner, id, read) }

    override suspend fun markAllRead(owner: InboxOwner) = proven(owner) { api.markAllRead(owner) }

    override suspend fun delete(
        owner: InboxOwner,
        id: String,
    ) = proven(owner) { api.delete(owner, id) }

    override suspend fun clearRead(owner: InboxOwner) = proven(owner) { api.clearRead(owner) }

    private suspend fun <T> proven(
        owner: InboxOwner,
        call: suspend () -> AppResult<T>,
    ): AppResult<T> {
        val first = call()
        if (owner != InboxOwner.Guest || first !is AppResult.Failure || !first.error.isSecretRefused()) return first
        return when (register()) {
            is AppResult.Success -> call()
            is AppResult.Failure -> first
        }
    }
}

internal fun AppError.isSecretRefused(): Boolean =
    this is AppError.Api && code == ApiErrorCodes.FORBIDDEN && reason == ApiErrorReasons.INSTALL_SECRET_INVALID
