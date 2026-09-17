package com.egyptexperiences.core.common.result

/**
 * A failure the UI can act on.
 *
 * [Api.code] is the backend's stable error code — the contract says the code is
 * what clients branch on, never the message (`be/common/.../ApiError.java`).
 * The message is for logs and for the one case where the backend is the only
 * thing that knows how to phrase the problem.
 */
sealed interface AppError {
    /** No route to the server: airplane mode, dead wifi, DNS. Retryable. */
    data object Network : AppError

    /** The server answered, but not in time. Retryable. */
    data object Timeout : AppError

    /** The server answered with `success: false`. */
    data class Api(
        val code: String,
        val message: String?,
        val httpStatus: Int?,
    ) : AppError

    /** The body did not match the contract. Never retryable — it is a bug. */
    data class Serialization(
        val message: String?,
    ) : AppError

    data class Unknown(
        val message: String?,
    ) : AppError
}

/** Error codes the UI branches on. Mirrors the codes the backend emits. */
object ApiErrorCodes {
    /** The hold expired before checkout completed — send the user back to date + party. */
    const val HOLD_EXPIRED = "HOLD_EXPIRED"

    /** Someone else took the last seats between the list and the hold. */
    const val SEATS_UNAVAILABLE = "SEATS_UNAVAILABLE"

    const val DEPARTURE_NOT_BOOKABLE = "DEPARTURE_NOT_BOOKABLE"
    const val BOOKING_NOT_FOUND = "BOOKING_NOT_FOUND"
    const val TRIP_NOT_FOUND = "TRIP_NOT_FOUND"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"

    /** `success: false` with no code — should not happen, but must not crash. */
    const val UNKNOWN = "UNKNOWN"

    /**
     * Client-side, not from the backend: the server answered with something
     * that is not the contract's envelope — a proxy's HTML 502, a captive
     * portal, an empty body from a crashed process.
     */
    const val NON_JSON_RESPONSE = "NON_JSON_RESPONSE"
}
