package eg.bahr.feature.settings.data

import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/** What Settings reads and changes: the stored app language, and the sign-in (M3-M0). */
internal interface SettingsRepository {
    val language: Flow<AppLanguage>

    /** Stores [language]; the whole app follows it (App re-themes every Compose root from the store). */
    suspend fun setLanguage(language: AppLanguage): AppResult<Unit>

    /** The device's sign-in, as the account row shows it. */
    val session: Flow<SessionState>

    /** Reads the stored sign-in once (it starts as Unknown). */
    suspend fun loadSession()

    /** Revokes the refresh token where the server can be reached, and forgets the sign-in either way. */
    suspend fun signOut()
}

internal class DefaultSettingsRepository(
    private val store: AppSettingsStore,
    private val authSession: AuthSession,
) : SettingsRepository {
    override val language: Flow<AppLanguage> = store.language

    override val session: Flow<SessionState> = authSession.state

    override suspend fun loadSession() {
        authSession.load()
    }

    override suspend fun signOut() = authSession.signOut()

    /**
     * A failed write (a full disk, a broken file) leaves the old language in place; it becomes a
     * failure the screen can report rather than a crash. Not network, so not `callApi`'s to catch.
     */
    override suspend fun setLanguage(language: AppLanguage): AppResult<Unit> =
        try {
            store.setLanguage(language)
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message))
        }
}
