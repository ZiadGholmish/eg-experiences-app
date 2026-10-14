package eg.bahr.feature.auth.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.auth.model.AccountDto
import eg.bahr.feature.auth.model.AuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Records what the view models asked for; each answer is a settable lambda. */
internal class FakeAuthRepository(
    initial: SessionState = SessionState.SignedOut,
    var signIn: suspend (AuthProvider, String, String, String?) -> AppResult<AccountDto> = { _, _, _, _ -> AppResult.Success(account()) },
    var updateProfile: suspend (String, String) -> AppResult<AccountDto> = { name, phone ->
        AppResult.Success(account(name = name, phone = phone, profileComplete = true))
    },
    var deleteAccount: suspend (String?) -> AppResult<Unit> = { AppResult.Success(Unit) },
) : AuthRepository {
    val state = MutableStateFlow(initial)
    override val session: StateFlow<SessionState> = state

    val signIns = mutableListOf<SignInCall>()
    val profiles = mutableListOf<Pair<String, String>>()
    val deletions = mutableListOf<String?>()

    data class SignInCall(
        val provider: AuthProvider,
        val idToken: String,
        val rawNonce: String,
        val appleName: String?,
    )

    override suspend fun signIn(
        provider: AuthProvider,
        idToken: String,
        rawNonce: String,
        appleName: String?,
    ): AppResult<AccountDto> {
        signIns += SignInCall(provider, idToken, rawNonce, appleName)
        return signIn.invoke(provider, idToken, rawNonce, appleName)
    }

    override suspend fun updateProfile(
        name: String,
        phone: String,
    ): AppResult<AccountDto> {
        profiles += name to phone
        return updateProfile.invoke(name, phone)
    }

    override suspend fun deleteAccount(appleAuthorizationCode: String?): AppResult<Unit> {
        deletions += appleAuthorizationCode
        return deleteAccount.invoke(appleAuthorizationCode)
    }

    companion object {
        fun account(
            name: String? = null,
            phone: String? = null,
            profileComplete: Boolean = false,
            providers: List<String> = listOf("google"),
        ) = AccountDto(
            id = "8a5d2c1e-0b7f-4e3a-9c6d-1f2e3a4b5c6d",
            name = name,
            phone = phone,
            locale = "ar",
            providers = providers,
            profileComplete = profileComplete,
        )
    }
}
