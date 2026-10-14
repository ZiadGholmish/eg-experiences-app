package eg.bahr.feature.auth.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.withDeviceId
import eg.bahr.core.network.auth.withoutAuth
import eg.bahr.core.network.callApi
import eg.bahr.feature.auth.model.AccountDto
import eg.bahr.feature.auth.model.AuthProvider
import eg.bahr.feature.auth.model.DeleteAccountRequest
import eg.bahr.feature.auth.model.SignInDto
import eg.bahr.feature.auth.model.SignInRequest
import eg.bahr.feature.auth.model.UpdateAccountRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * The account operations of openapi (M3-B1): `signInWithProvider`, `getMe`, `updateMe`, `deleteMe`.
 * Refresh and sign-out belong to core:network's `AuthSession`, which every request goes through.
 */
internal class AuthApiService(
    private val client: HttpClient,
) {
    /**
     * Without our token (this is how one is obtained), with `X-Device-Id`, so the server attaches this
     * device's anonymous holds and waiting-list entries to the account.
     */
    suspend fun signIn(
        provider: AuthProvider,
        request: SignInRequest,
    ): AppResult<SignInDto> =
        callApi {
            client.post("auth/${provider.wire}") {
                withoutAuth()
                withDeviceId()
                setBody(request)
            }
        }

    suspend fun me(): AppResult<AccountDto> = callApi { client.get("me") }

    suspend fun updateMe(request: UpdateAccountRequest): AppResult<AccountDto> = callApi { client.patch("me") { setBody(request) } }

    suspend fun deleteMe(request: DeleteAccountRequest): AppResult<Unit> = callApi { client.delete("me") { setBody(request) } }
}
