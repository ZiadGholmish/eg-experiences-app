package eg.bahr.feature.auth.data

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.auth.model.AccountDto
import eg.bahr.feature.auth.model.AuthProvider
import eg.bahr.feature.auth.model.DeleteAccountRequest
import eg.bahr.feature.auth.model.SignInRequest
import eg.bahr.feature.auth.model.UpdateAccountRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The first-sign-in sheet's ask (M3-M0b, handoff §2 "Complete your profile"): what the account already
 * has, to prefill, and the provider it came from ("From your Google account").
 */
internal data class ProfilePrompt(
    val name: String,
    val phone: String,
    val provider: String?,
)

/** What has been typed in the first-sign-in sheet, as typed (the phone is normalised only to send). */
internal data class ProfileDraft(
    val name: String,
    val phone: String,
)

/** Sign-in, the profile and deletion, each keeping core:network's [AuthSession] in step with the server. */
internal interface AuthRepository {
    val session: StateFlow<SessionState>

    /**
     * Set when a sign-in comes back without a complete profile, until the sheet saves or is left for later.
     * App-wide (this is a Koin single), because the sheet shows over whatever screen the sign-in started
     * from, and that screen may leave the composition the moment the session flips to signed in.
     */
    val profilePrompt: StateFlow<ProfilePrompt?>

    /** Asks for the name and phone over the current screen ([profilePrompt]). */
    fun askForProfile(prompt: ProfilePrompt)

    /** The sheet is done: saved, or left for later (checkout asks again). */
    fun closeProfilePrompt()

    /**
     * What has been typed in the open sheet, the one source every sheet shows: on iOS each tab is its own
     * Compose root with its own, long-lived view models, and a tab tapped while the sheet is open moves
     * the sheet to that tab's root, and back (M3-M0b review R2-7, R3-1). Null until something is typed;
     * cleared with the ask.
     */
    val profileDraft: StateFlow<ProfileDraft?>

    fun setProfileDraft(draft: ProfileDraft)

    /** Exchanges the provider's ID token for ours; the session is signed in on success. */
    suspend fun signIn(
        provider: AuthProvider,
        idToken: String,
        rawNonce: String,
        appleName: String?,
    ): AppResult<AccountDto>

    suspend fun updateProfile(
        name: String,
        phone: String,
    ): AppResult<AccountDto>

    /** Deletes the account; the session is forgotten on success. */
    suspend fun deleteAccount(appleAuthorizationCode: String?): AppResult<Unit>
}

internal class DefaultAuthRepository(
    private val api: AuthApiService,
    private val authSession: AuthSession,
) : AuthRepository {
    override val session: StateFlow<SessionState> = authSession.state

    private val prompt = MutableStateFlow<ProfilePrompt?>(null)
    override val profilePrompt: StateFlow<ProfilePrompt?> = prompt.asStateFlow()

    private val draft = MutableStateFlow<ProfileDraft?>(null)
    override val profileDraft: StateFlow<ProfileDraft?> = draft.asStateFlow()

    override fun setProfileDraft(draft: ProfileDraft) {
        if (prompt.value != null) this.draft.value = draft
    }

    override fun askForProfile(prompt: ProfilePrompt) {
        draft.value = null
        this.prompt.value = prompt
    }

    override fun closeProfilePrompt() {
        draft.value = null
        prompt.value = null
    }

    override suspend fun signIn(
        provider: AuthProvider,
        idToken: String,
        rawNonce: String,
        appleName: String?,
    ): AppResult<AccountDto> =
        when (val result = api.signIn(provider, SignInRequest(idToken = idToken, nonce = rawNonce, name = appleName))) {
            is AppResult.Success -> {
                val signIn = result.data
                authSession.signedIn(
                    tokens = AuthTokens(signIn.accessToken, signIn.accessTokenExpiresAt, signIn.refreshToken, signIn.refreshTokenExpiresAt),
                    accountName = signIn.account.name,
                    profileComplete = signIn.account.profileComplete,
                    providers = signIn.account.providers,
                )
                if (!signIn.account.profileComplete) askForProfile(signIn.account.toPrompt())
                AppResult.Success(signIn.account)
            }
            is AppResult.Failure -> result
        }

    override suspend fun updateProfile(
        name: String,
        phone: String,
    ): AppResult<AccountDto> =
        when (val result = api.updateMe(UpdateAccountRequest(name = name, phone = phone))) {
            is AppResult.Success -> {
                val account = result.data
                authSession.updateAccount(account.name, account.profileComplete, account.providers)
                result
            }
            is AppResult.Failure -> result
        }

    override suspend fun deleteAccount(appleAuthorizationCode: String?): AppResult<Unit> =
        when (val result = api.deleteMe(DeleteAccountRequest(appleAuthorizationCode))) {
            is AppResult.Success -> {
                // The server revoked every refresh token; nothing is left to sign out of.
                authSession.clear()
                result
            }
            is AppResult.Failure -> result
        }
}

private fun AccountDto.toPrompt() = ProfilePrompt(name = name.orEmpty(), phone = phone.orEmpty(), provider = providers.firstOrNull())
