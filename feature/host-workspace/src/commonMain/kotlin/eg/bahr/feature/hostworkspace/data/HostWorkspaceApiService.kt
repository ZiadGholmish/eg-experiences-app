package eg.bahr.feature.hostworkspace.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.withoutAuth
import eg.bahr.core.network.callApi
import eg.bahr.feature.hostworkspace.model.BroadcastReceiptDto
import eg.bahr.feature.hostworkspace.model.BroadcastRequest
import eg.bahr.feature.hostworkspace.model.HostDepartureDto
import eg.bahr.feature.hostworkspace.model.HostDepartureSummaryDto
import eg.bahr.feature.hostworkspace.model.HostMeDto
import eg.bahr.feature.hostworkspace.model.HostTokensDto
import eg.bahr.feature.hostworkspace.model.ManualBookingRequest
import eg.bahr.feature.hostworkspace.model.PasswordChangeRequest
import eg.bahr.feature.hostworkspace.model.PasswordSignInDto
import eg.bahr.feature.hostworkspace.model.PasswordSignInRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Host mode's operations of openapi (M5-B0…B3): `signInWithPassword`, `changePassword`, `getHostMe`,
 * `listHostDepartures`, `getHostDeparture`, `broadcastToDeparture`, `createManualBooking`, `markManualBookingPaid`.
 * [client] is core:network's host client: the host's bearer, never the traveller's. Refresh and sign-out belong to
 * `HostSession`. Nothing here is retried on its own; `X-Requested-With` is never sent, so the server always takes the
 * body path for the refresh token.
 */
internal class HostWorkspaceApiService(
    private val client: HttpClient,
) {
    /** Without a token: this is how one is obtained. */
    suspend fun signIn(request: PasswordSignInRequest): AppResult<PasswordSignInDto> =
        callApi {
            client.post("auth/password") {
                withoutAuth()
                setBody(request)
            }
        }

    /** With the change token in the body, not as a bearer: it opens no other route. */
    suspend fun changePassword(request: PasswordChangeRequest): AppResult<HostTokensDto> =
        callApi {
            client.post("auth/password/change") {
                withoutAuth()
                setBody(request)
            }
        }

    suspend fun me(): AppResult<HostMeDto> = callApi { client.get("host/me") }

    /** [from] null: the server's today in Cairo. */
    suspend fun departures(from: String? = null): AppResult<List<HostDepartureSummaryDto>> =
        callApi { client.get("host/departures") { from?.let { parameter("from", it) } } }

    suspend fun departure(id: String): AppResult<HostDepartureDto> = callApi { client.get("host/departures/$id") }

    suspend fun broadcast(
        departureId: String,
        message: String,
    ): AppResult<BroadcastReceiptDto> =
        callApi { client.post("host/departures/$departureId/broadcast") { setBody(BroadcastRequest(message)) } }

    /** [idempotencyKey]: one per booking the host means to enter, the same on every retry of it (see the repository). */
    suspend fun createManualBooking(
        departureId: String,
        idempotencyKey: String,
        request: ManualBookingRequest,
    ): AppResult<HostDepartureDto> =
        callApi {
            client.post("host/departures/$departureId/manual-bookings") {
                header(IDEMPOTENCY_KEY, idempotencyKey)
                setBody(request)
            }
        }

    suspend fun markPaid(
        departureId: String,
        bookingId: String,
    ): AppResult<HostDepartureDto> = callApi { client.post("host/departures/$departureId/manual-bookings/$bookingId/paid") }

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
    }
}
