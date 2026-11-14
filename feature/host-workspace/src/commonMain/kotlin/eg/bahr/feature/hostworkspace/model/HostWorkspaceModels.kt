package eg.bahr.feature.hostworkspace.model

import kotlinx.serialization.Serializable

/*
 * The host-mode shapes of openapi (M5-B0…M5-B5), field for field. Fields M5-B5 made required (`HostMe`'s profile and
 * login, `HostDepartureTraveller.id`) are nullable here with a default: a server before M5-B5 (bahr-be main at the time
 * of M5-M1) leaves them out, and the app must still read its answers. The app hides what needs them when absent.
 */

/** openapi `RefreshTokenIn`: the app always takes its refresh token in the body (cookie mode is the web's). */
internal const val REFRESH_TOKEN_IN_BODY = "BODY"

/** openapi `PasswordSignInRequest`. `refreshTokenIn` is sent explicitly, though `BODY` is the default. */
@Serializable
internal data class PasswordSignInRequest(
    val email: String,
    val password: String,
    val refreshTokenIn: String = REFRESH_TOKEN_IN_BODY,
)

/** openapi `PasswordSignIn`: exactly one of [session] and [passwordChange], as [outcome] says. */
@Serializable
internal data class PasswordSignInDto(
    val outcome: String,
    val session: HostTokensDto? = null,
    val passwordChange: PasswordChangeTicketDto? = null,
) {
    companion object {
        const val SIGNED_IN = "SIGNED_IN"
        const val PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED"
    }
}

/**
 * openapi `Tokens`. [refreshToken] is optional in the contract (a cookie session leaves it out); body mode always sends
 * it, and the repository refuses an answer without one rather than keeping a session it could never refresh.
 */
@Serializable
internal data class HostTokensDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String,
    val roles: List<String> = emptyList(),
    val serverNow: String? = null,
)

/** openapi `PasswordChangeTicket`: kept in memory only, for `changePassword`. */
@Serializable
internal data class PasswordChangeTicketDto(
    val changeToken: String,
    val expiresAt: String,
)

/** openapi `PasswordChangeRequest`. */
@Serializable
internal data class PasswordChangeRequest(
    val changeToken: String,
    val newPassword: String,
    val refreshTokenIn: String = REFRESH_TOKEN_IN_BODY,
)

/** openapi `HostMe` (`getHostMe`). `avatar` is not read: host mode shows the name's initials. */
@Serializable
internal data class HostMeDto(
    val id: String,
    val slug: String,
    val name: String,
    val email: String,
    val kind: String? = null,
    val role: String? = null,
    val bio: String? = null,
    val phone: String? = null,
    val verified: Boolean? = null,
    val memberSince: String? = null,
    val tripsRun: Int? = null,
    val login: HostLoginStateDto? = null,
)

/** openapi `HostLoginState`. An unknown [status] counts as ACTIVE. */
@Serializable
internal data class HostLoginStateDto(
    val status: String,
    val lockedUntil: String? = null,
    val createdAt: String,
)

/** openapi `HostDepartureSummary` (`listHostDepartures`): one line of the departure picker. */
@Serializable
internal data class HostDepartureSummaryDto(
    val id: String,
    val date: String,
    val dayLabel: String,
    val departTime: String,
    val status: String,
    val tripSlug: String,
    val tripTitle: String,
    val seatsBooked: Int,
    val seatsHeld: Int,
    val capacity: Int,
)

/** openapi `HostDeparture` (`getHostDeparture`, and the answer to a manual booking or mark-paid): the manifest. */
@Serializable
internal data class HostDepartureDto(
    val id: String,
    val date: String,
    val dayLabel: String,
    val status: String,
    val trip: HostDepartureTripDto,
    val seatsBooked: Int,
    val seatsHeld: Int,
    val capacity: Int,
    val collected: HostMoneyDto,
    val bookings: List<HostTravellerDto>,
    val broadcast: HostBroadcastDto? = null,
)

@Serializable
internal data class HostDepartureTripDto(
    val slug: String,
    val title: String,
    val departTime: String,
    val returnTime: String? = null,
)

/** openapi `Money`. Formatted only; never added up on the device. */
@Serializable
internal data class HostMoneyDto(
    val amount: Long,
    val amountPiastres: Long? = null,
    val currency: String,
)

/** openapi `HostDepartureTraveller`. [id] since M5-B5 (what mark-paid names); [phone] null once the guest was erased. */
@Serializable
internal data class HostTravellerDto(
    val id: String? = null,
    val ref: String,
    val name: String,
    val phone: String? = null,
    val partySize: Int,
    val paid: Boolean,
    val source: String,
    val bookedAt: String,
) {
    val manual: Boolean get() = source == SOURCE_MANUAL

    companion object {
        const val SOURCE_MANUAL = "MANUAL"
    }
}

/** openapi `HostDepartureBroadcast`: the date's last broadcast. */
@Serializable
internal data class HostBroadcastDto(
    val lastSentAt: String,
    val recipients: Int,
    val nextAllowedAt: String,
)

/** `broadcastToDeparture`'s body. */
@Serializable
internal data class BroadcastRequest(
    val message: String,
)

/** openapi `HostBroadcastReceipt`. */
@Serializable
internal data class BroadcastReceiptDto(
    val recipients: Int,
    val queuedAt: String,
    val nextAllowedAt: String,
)

/** `createManualBooking`'s body. [locale] is always sent (M5-B3 review #18: a retry must fingerprint the same). */
@Serializable
internal data class ManualBookingRequest(
    val name: String,
    val phone: String,
    val partySize: Int,
    val paid: Boolean,
    val locale: String,
)

/** openapi `HostDepartureStatus`. */
internal object HostDepartureStatus {
    const val OPEN = "OPEN"
    const val CANCELLED = "CANCELLED"
    const val COMPLETED = "COMPLETED"
}
