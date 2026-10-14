package eg.bahr.core.network.auth

import co.touchlab.kermit.Logger
import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/** Marks a request that must go without our bearer token (sign-in, refresh, sign-out). */
private val NoAuth = AttributeKey<Unit>("BahrNoAuth")

/** Marks a request that carries `X-Device-Id` (sign-in, holds, waiting-list joins; openapi `DeviceId`). */
private val DeviceId = AttributeKey<Unit>("BahrDeviceId")

/** Send this request without the signed-in user's token. */
fun HttpRequestBuilder.withoutAuth() = attributes.put(NoAuth, Unit)

/**
 * Send this device's id with this request (openapi `DeviceId`): only on sign-in, holds and waiting-list
 * joins, the requests the server attaches to the account this device signs in to (M3-B1).
 */
fun HttpRequestBuilder.withDeviceId() = attributes.put(DeviceId, Unit)

internal const val HEADER_DEVICE_ID = "X-Device-Id"

/**
 * Bearer token on every request while signed in, one retry after a 401 with a refreshed token, and
 * `X-Device-Id` where asked for. A plugin rather than Ktor's `Auth`, whose bearer provider waits for a
 * `WWW-Authenticate` challenge and keeps its own token cache beside the secure store; [AuthSession] owns
 * the rules (see its doc).
 */
internal fun bahrAuth(
    session: AuthSession,
    deviceIds: DeviceIdSource,
) = createClientPlugin("BahrAuth") {
    on(Send) { request ->
        if (request.attributes.contains(DeviceId)) {
            deviceIdOrNull(deviceIds)?.let { request.headers[HEADER_DEVICE_ID] = it }
        }
        if (request.attributes.contains(NoAuth)) return@on proceed(request)

        val token = session.accessTokenForRequest()
        token?.let { request.headers[HttpHeaders.Authorization] = bearer(it) }
        val call = proceed(request)
        if (token == null || call.response.status != HttpStatusCode.Unauthorized) return@on call

        // A 401 the session cannot cure drops the tokens (plan: "on 401 drop the tokens"): the refresh was
        // refused, lost, or could not be sent although this request reached the server.
        val fresh = session.refreshAfterRejection(token)
        if (fresh == null) {
            session.dropIfCurrent(token)
            return@on call
        }
        request.headers[HttpHeaders.Authorization] = bearer(fresh)
        val retried = proceed(request)
        // Refreshed and still refused (the account was deleted, the token revoked): signed out (review #2).
        if (retried.response.status == HttpStatusCode.Unauthorized) session.dropIfCurrent(fresh)
        retried
    }
}

private fun bearer(token: String) = "Bearer $token"

/** A device id the store cannot give is left out: the header is optional, the request still goes. */
private suspend fun deviceIdOrNull(deviceIds: DeviceIdSource): String? =
    try {
        deviceIds.deviceId()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.withTag("BahrAuth").w(e) { "No device id for this request" }
        null
    }

/** openapi `RefreshRequest` (refresh and sign-out). */
@Serializable
internal data class RefreshRequestDto(
    val refreshToken: String,
)

/** openapi `Tokens`. [roles] is not needed on the device. */
@Serializable
internal data class TokensDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val roles: List<String> = emptyList(),
)

/** [client] is resolved on first use (see `networkModule`): building the session must not build the client. */
internal class HttpSessionRemote(
    private val clientProvider: () -> HttpClient,
) : SessionRemote {
    private val client: HttpClient by lazy(clientProvider)

    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        val result =
            callApi<TokensDto> {
                client.post("auth/refresh") {
                    withoutAuth()
                    setBody(RefreshRequestDto(refreshToken))
                }
            }
        return when (result) {
            is AppResult.Success ->
                result.data.run {
                    RefreshOutcome.Refreshed(AuthTokens(accessToken, accessTokenExpiresAt, this.refreshToken, refreshTokenExpiresAt))
                }
            is AppResult.Failure -> result.error.toOutcome()
        }
    }

    override suspend fun revoke(refreshToken: String) {
        callApi<Unit> {
            client.post("auth/sign-out") {
                withoutAuth()
                setBody(RefreshRequestDto(refreshToken))
            }
        }
    }

    /** Refused, lost or never sent (see [AuthSession]). */
    private fun AppError.toOutcome(): RefreshOutcome =
        when (this) {
            AppError.Network -> RefreshOutcome.NotSent
            // 429: refused before the token was looked at, so it is still good (M3-M0 review #6).
            is AppError.Api ->
                when (httpStatus) {
                    TOO_MANY_REQUESTS -> RefreshOutcome.NotSent
                    in CLIENT_ERRORS -> RefreshOutcome.Rejected
                    else -> RefreshOutcome.Lost
                }
            AppError.Timeout, is AppError.Serialization, is AppError.Unknown -> RefreshOutcome.Lost
        }

    private companion object {
        val CLIENT_ERRORS = 400..499
        const val TOO_MANY_REQUESTS = 429
    }
}
