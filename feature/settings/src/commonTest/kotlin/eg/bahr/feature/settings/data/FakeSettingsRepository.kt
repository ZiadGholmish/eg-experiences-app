package eg.bahr.feature.settings.data

import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.settings.model.AccountDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull

/**
 * The stored language and the account in memory. [stored] null = the store has not answered yet. With
 * [failWith], a language write fails and the stored language stays as it was. [me] and [update] are
 * the server's answers to `getMe` and `updateMe`.
 */
internal class FakeSettingsRepository(
    initial: AppLanguage? = AppLanguage.ARABIC,
    var failWith: AppError? = null,
    account: SessionState = SessionState.SignedOut,
    var me: suspend () -> AppResult<AccountDto> = { AppResult.Success(account()) },
    var update: suspend (String, String) -> AppResult<AccountDto> = { name, phone ->
        AppResult.Success(account(name = name, phone = phone))
    },
) : SettingsRepository {
    val sessionState = MutableStateFlow(account)
    var signOuts = 0
    var meReads = 0
    val updates = mutableListOf<Pair<String, String>>()

    override val session: Flow<SessionState> = sessionState

    override suspend fun loadSession() = Unit

    override suspend fun signOut() {
        signOuts++
        accountState.value = null
        sessionState.value = SessionState.SignedOut
    }

    val storedAppearance = MutableStateFlow(AppAppearance.default)
    val appearanceWrites = mutableListOf<AppAppearance>()

    override val appearance: Flow<AppAppearance> = storedAppearance

    override suspend fun setAppearance(appearance: AppAppearance): AppResult<Unit> {
        appearanceWrites += appearance
        failWith?.let { return AppResult.Failure(it) }
        storedAppearance.value = appearance
        return AppResult.Success(Unit)
    }

    val stored = MutableStateFlow(initial)
    val writes = mutableListOf<AppLanguage>()

    override val language: Flow<AppLanguage> = stored.filterNotNull()

    override suspend fun setLanguage(language: AppLanguage): AppResult<Unit> {
        writes += language
        failWith?.let { return AppResult.Failure(it) }
        stored.value = language
        return AppResult.Success(Unit)
    }

    val accountState = MutableStateFlow<AccountDto?>(null)
    override val account: Flow<AccountDto?> =
        combine(accountState, sessionState) { account, session ->
            if (session !is SessionState.SignedIn) accountState.value = null
            validFor(account, session)
        }

    override suspend fun refreshAccount(): AppResult<AccountDto> {
        meReads++
        return me().also { result ->
            if (result !is AppResult.Success) return@also
            accountState.value = result.data
            // As the real one: the server's account becomes the session's summary (review R2-1).
            if (sessionState.value is SessionState.SignedIn) {
                sessionState.value = SessionState.SignedIn(result.data.name, result.data.profileComplete, result.data.providers.toSet())
            }
        }
    }

    override suspend fun updateProfile(
        name: String,
        phone: String,
    ): AppResult<AccountDto> {
        updates += name to phone
        return update(name, phone).also { if (it is AppResult.Success) accountState.value = it.data }
    }

    val saved = MutableStateFlow(false)
    override val savedNotice: StateFlow<Boolean> = saved

    override fun setSavedNotice(shown: Boolean) {
        saved.value = shown
    }

    companion object {
        /** The session of [account]'s default person, signed in with a complete profile. */
        val SIGNED_IN = SessionState.SignedIn("منة الله حسن", profileComplete = true, providers = setOf("google"))

        fun account(
            name: String? = "منة الله حسن",
            phone: String? = "01012345678",
            email: String? = "menna.hassan@gmail.com",
            providers: List<String> = listOf("google"),
        ) = AccountDto(
            id = "8a5d2c1e-0b7f-4e3a-9c6d-1f2e3a4b5c6d",
            name = name,
            phone = phone,
            email = email,
            providers = providers,
            profileComplete = name != null && phone != null,
        )
    }
}
