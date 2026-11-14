package eg.bahr.feature.hostworkspace.data

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.ApiErrorReasons
import eg.bahr.core.network.auth.HostSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.hostworkspace.model.BroadcastReceiptDto
import eg.bahr.feature.hostworkspace.model.HostDepartureDto
import eg.bahr.feature.hostworkspace.model.HostDepartureSummaryDto
import eg.bahr.feature.hostworkspace.model.HostMeDto
import eg.bahr.feature.hostworkspace.model.HostTokensDto
import eg.bahr.feature.hostworkspace.model.ManualBookingRequest
import eg.bahr.feature.hostworkspace.model.PasswordChangeRequest
import eg.bahr.feature.hostworkspace.model.PasswordSignInDto
import eg.bahr.feature.hostworkspace.model.PasswordSignInRequest
import kotlinx.coroutines.flow.StateFlow

/** How a password sign-in ended (openapi `PasswordSignIn.outcome`). */
internal sealed interface HostSignInOutcome {
    /** Signed in: the tokens are kept in host mode's own secure item. */
    data object SignedIn : HostSignInOutcome

    /** A new or reset login: nothing is signed in yet; [changeToken] (memory only) opens `changePassword` and nothing else. */
    data class PasswordChangeRequired(
        val changeToken: String,
    ) : HostSignInOutcome
}

/** A manual booking the host enters (`createManualBooking`'s body), as the form has it. */
internal data class ManualBooking(
    val name: String,
    val phone: String,
    val partySize: Int,
    val paid: Boolean,
    val locale: String,
)

/** Host mode's data (M5-M1): the host's own session and the `/api/v1/host/` routes. */
internal interface HostWorkspaceRepository {
    val session: StateFlow<SessionState>

    suspend fun loadSession(): SessionState

    suspend fun signIn(
        email: String,
        password: String,
    ): AppResult<HostSignInOutcome>

    suspend fun changePassword(
        changeToken: String,
        newPassword: String,
    ): AppResult<Unit>

    /** Ends host mode's sign-in only; the traveller's stays. */
    suspend fun signOut()

    suspend fun me(): AppResult<HostMeDto>

    suspend fun departures(): AppResult<List<HostDepartureSummaryDto>>

    suspend fun departure(id: String): AppResult<HostDepartureDto>

    suspend fun broadcast(
        departureId: String,
        message: String,
    ): AppResult<BroadcastReceiptDto>

    /**
     * Never retried here: the caller sends the same [idempotencyKey] again only when the host asks (openapi: clients must
     * never retry it automatically, and never with a new key).
     */
    suspend fun createManualBooking(
        departureId: String,
        idempotencyKey: String,
        booking: ManualBooking,
    ): AppResult<HostDepartureDto>

    suspend fun markPaid(
        departureId: String,
        bookingId: String,
    ): AppResult<HostDepartureDto>
}

internal class DefaultHostWorkspaceRepository(
    private val api: HostWorkspaceApiService,
    private val hostSession: HostSession,
) : HostWorkspaceRepository {
    override val session: StateFlow<SessionState> get() = hostSession.state

    override suspend fun loadSession(): SessionState = hostSession.load()

    override suspend fun signIn(
        email: String,
        password: String,
    ): AppResult<HostSignInOutcome> =
        when (val result = api.signIn(PasswordSignInRequest(email = email.trim(), password = password))) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val answer = result.data
                val change = answer.passwordChange
                val tokens = answer.session?.toTokens()
                when {
                    answer.outcome == PasswordSignInDto.PASSWORD_CHANGE_REQUIRED && change != null ->
                        AppResult.Success(HostSignInOutcome.PasswordChangeRequired(change.changeToken))
                    answer.outcome == PasswordSignInDto.SIGNED_IN && tokens != null -> {
                        hostSession.signedIn(tokens)
                        AppResult.Success(HostSignInOutcome.SignedIn)
                    }
                    // An outcome this build does not know, or a session without a refresh token: nothing to keep.
                    else -> AppResult.Failure(AppError.Serialization("Unusable password sign-in answer: ${answer.outcome}"))
                }
            }
        }

    override suspend fun changePassword(
        changeToken: String,
        newPassword: String,
    ): AppResult<Unit> =
        when (val result = api.changePassword(PasswordChangeRequest(changeToken = changeToken, newPassword = newPassword))) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val tokens = result.data.toTokens()
                if (tokens == null) {
                    AppResult.Failure(AppError.Serialization("Password changed but no refresh token came back"))
                } else {
                    hostSession.signedIn(tokens)
                    AppResult.Success(Unit)
                }
            }
        }

    override suspend fun signOut() = hostSession.signOut()

    override suspend fun me(): AppResult<HostMeDto> =
        api.me().also { result ->
            if (result is AppResult.Success) hostSession.updateName(result.data.name)
        }

    override suspend fun departures(): AppResult<List<HostDepartureSummaryDto>> = api.departures()

    override suspend fun departure(id: String): AppResult<HostDepartureDto> = api.departure(id)

    override suspend fun broadcast(
        departureId: String,
        message: String,
    ): AppResult<BroadcastReceiptDto> = api.broadcast(departureId, message)

    override suspend fun createManualBooking(
        departureId: String,
        idempotencyKey: String,
        booking: ManualBooking,
    ): AppResult<HostDepartureDto> =
        api.createManualBooking(
            departureId,
            idempotencyKey,
            ManualBookingRequest(
                name = booking.name,
                phone = booking.phone,
                partySize = booking.partySize,
                paid = booking.paid,
                locale = booking.locale,
            ),
        )

    override suspend fun markPaid(
        departureId: String,
        bookingId: String,
    ): AppResult<HostDepartureDto> = api.markPaid(departureId, bookingId)

    private fun HostTokensDto.toTokens(): AuthTokens? =
        refreshToken?.let { AuthTokens(accessToken, accessTokenExpiresAt, it, refreshTokenExpiresAt) }
}

/** What a failed host request means for the screen (M5-W1 review: 403 and 404 on host routes read alike). */
internal enum class HostRefusal {
    /** FORBIDDEN with reason HOST_SUSPENDED: every host route refuses until staff reinstate the host. */
    Suspended,

    /** Any other FORBIDDEN, or NOT_FOUND: not this host's, or gone. "Not available", never which. */
    NotAvailable,

    /** Anything else: the error's own message, and a way to try again. */
    Other,
}

internal fun AppError.hostRefusal(): HostRefusal =
    when {
        this !is AppError.Api -> HostRefusal.Other
        code == ApiErrorCodes.FORBIDDEN && reason == ApiErrorReasons.HOST_SUSPENDED -> HostRefusal.Suspended
        code == ApiErrorCodes.FORBIDDEN || code == ApiErrorCodes.NOT_FOUND -> HostRefusal.NotAvailable
        else -> HostRefusal.Other
    }

/**
 * Whether a manual booking attempt ended without telling whether it was recorded: no answer (network, timeout), a
 * server error (5xx, or a body that is not the contract's), or a key already used (M5-W1 review #1: an earlier attempt
 * with this key may have gone through). The manifest is read again and the host asked to check it before adding again.
 */
internal fun AppError.outcomeUnclear(): Boolean =
    when (this) {
        AppError.Network, AppError.Timeout, is AppError.Serialization, is AppError.Unknown -> true
        is AppError.Api ->
            reason == ApiErrorReasons.IDEMPOTENCY_KEY_REUSED ||
                (httpStatus ?: 0) >= SERVER_ERROR_FLOOR ||
                code == ApiErrorCodes.NON_JSON_RESPONSE ||
                code == ApiErrorCodes.UNKNOWN
    }

private const val SERVER_ERROR_FLOOR = 500
