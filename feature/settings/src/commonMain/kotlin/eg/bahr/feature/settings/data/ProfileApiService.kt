package eg.bahr.feature.settings.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.settings.model.AccountDto
import eg.bahr.feature.settings.model.UpdateAccountRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody

/** openapi `getMe` and `updateMe` (M3-B1), for Profile and Edit personal info (M3-M0b). */
internal class ProfileApiService(
    private val client: HttpClient,
) {
    suspend fun me(): AppResult<AccountDto> = callApi { client.get("me") }

    suspend fun updateMe(request: UpdateAccountRequest): AppResult<AccountDto> = callApi { client.patch("me") { setBody(request) } }
}
