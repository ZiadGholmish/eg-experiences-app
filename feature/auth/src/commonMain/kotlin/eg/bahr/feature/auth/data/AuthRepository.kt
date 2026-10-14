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
import kotlinx.coroutines.flow.StateFlow

/** Sign-in, the profile and deletion, each keeping core:network's [AuthSession] in step with the server. */
internal interface AuthRepository {
    val session: StateFlow<SessionState>

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
