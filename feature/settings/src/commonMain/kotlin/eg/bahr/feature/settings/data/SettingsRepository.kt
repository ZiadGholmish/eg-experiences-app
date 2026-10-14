package eg.bahr.feature.settings.data

import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.settings.model.AccountDto
import eg.bahr.feature.settings.model.UpdateAccountRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.coroutines.cancellation.CancellationException

/**
 * What Profile (the Settings tab before M3-M0b) reads and changes: the stored app language, the sign-in,
 * and the signed-in account (`getMe` / `updateMe`).
 */
internal interface SettingsRepository {
    val language: Flow<AppLanguage>

    /** Stores [language]; the whole app follows it (App re-themes every Compose root from the store). */
    suspend fun setLanguage(language: AppLanguage): AppResult<Unit>

    /** Profile → Appearance (M3-M0b): Light until the person picks otherwise. */
    val appearance: Flow<AppAppearance>

    /** Stores [appearance]; every screen (and iOS's tab bar) follows it. */
    suspend fun setAppearance(appearance: AppAppearance): AppResult<Unit>

    /** The device's sign-in, as Profile shows it. */
    val session: Flow<SessionState>

    /** Reads the stored sign-in once (it starts as Unknown). */
    suspend fun loadSession()

    /** Revokes the refresh token where the server can be reached, and forgets the sign-in either way. */
    suspend fun signOut()

    /**
     * The signed-in account as last read or saved, for Profile and Edit personal info alike. Null before
     * the first read, once the session is no longer signed in, and once the session's summary (name,
     * profile complete, providers) no longer matches it: the account changed elsewhere (M3-M0b review #2: feature:auth's
     * "Complete your profile" sheet saves through its own `updateMe`), so it must be read again. The
     * repository decides this from the session itself, whoever is listening (review #9).
     */
    val account: Flow<AccountDto?>

    /** Reads the account (`getMe`) into [account]. */
    suspend fun refreshAccount(): AppResult<AccountDto>

    /** Saves the name and phone (`updateMe`) into [account] and the session's summary. */
    suspend fun updateProfile(
        name: String,
        phone: String,
    ): AppResult<AccountDto>

    /** Set by a save, until Profile has shown its "Saved" snackbar. */
    val savedNotice: StateFlow<Boolean>

    fun setSavedNotice(shown: Boolean)
}

internal class DefaultSettingsRepository(
    private val store: AppSettingsStore,
    private val authSession: AuthSession,
    private val api: ProfileApiService,
) : SettingsRepository {
    override val language: Flow<AppLanguage> = store.language

    override val appearance: Flow<AppAppearance> = store.appearance

    /** As [setLanguage]: a failed write keeps the old appearance and is reported, not thrown. */
    override suspend fun setAppearance(appearance: AppAppearance): AppResult<Unit> =
        try {
            store.setAppearance(appearance)
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message))
        }

    override val session: Flow<SessionState> = authSession.state

    private val _account = MutableStateFlow<AccountDto?>(null)
    override val account: Flow<AccountDto?> =
        combine(_account, authSession.state) { account, session ->
            // The session ended, here or elsewhere (a refused refresh, a deleted account): the account goes
            // with it, so the next person can never see it (review R2-4).
            if (session !is SessionState.SignedIn) _account.value = null
            validFor(account, session)
        }.distinctUntilChanged()

    private val _savedNotice = MutableStateFlow(false)
    override val savedNotice: StateFlow<Boolean> = _savedNotice.asStateFlow()

    override suspend fun loadSession() {
        authSession.load()
    }

    override suspend fun signOut() {
        _account.value = null
        authSession.signOut()
    }

    /**
     * The server wins (M3-M0b review R2-1): a read also brings the session's summary up to date, so a name
     * changed on another phone (or kept by an older build) neither hides the account nor stays on screen.
     */
    override suspend fun refreshAccount(): AppResult<AccountDto> =
        api.me().also { result ->
            if (result is AppResult.Success) {
                val account = result.data
                _account.value = account
                authSession.updateAccount(account.name, account.profileComplete, account.providers)
            }
        }

    override suspend fun updateProfile(
        name: String,
        phone: String,
    ): AppResult<AccountDto> =
        api.updateMe(UpdateAccountRequest(name = name, phone = phone)).also { result ->
            if (result is AppResult.Success) {
                val saved = result.data
                _account.value = saved
                // My trips, the sign-in sheet and the next launch read the name from the session.
                authSession.updateAccount(saved.name, saved.profileComplete, saved.providers)
            }
        }

    override fun setSavedNotice(shown: Boolean) {
        _savedNotice.value = shown
    }

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

/**
 * [account] while it still describes [session]'s signed-in account, else null. Shared with the test fake,
 * so both apply one rule.
 */
internal fun validFor(
    account: AccountDto?,
    session: SessionState,
): AccountDto? =
    account?.takeIf {
        session is SessionState.SignedIn &&
            it.name == session.accountName &&
            it.profileComplete == session.profileComplete &&
            it.providers.toSet() == session.providers
    }
