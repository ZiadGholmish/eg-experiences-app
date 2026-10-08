package eg.bahr.core.common.result

/**
 * A failure the UI can act on.
 *
 * [Api.code] is the backend's stable error code — the contract says the code is
 * what clients branch on, never the message (`ApiError` in `../docs/api/openapi.yaml`).
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

/**
 * Error codes the UI branches on.
 *
 * The first group is exactly the `ApiError.code` enum in `../docs/api/openapi.yaml`
 * (the contract, whose source of truth is `bahr-be/docs/api/openapi.yaml`); keep
 * it in step with that enum. A code without a localised message falls back to
 * the generic one (see `AppError.localizedMessage`), so listing a code here
 * changes no behaviour on its own.
 */
object ApiErrorCodes {
    // ---- From the contract (`ApiError.code`) ----

    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val IDENTITY_REQUIRED = "IDENTITY_REQUIRED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
    const val METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED"
    const val NOT_ACCEPTABLE = "NOT_ACCEPTABLE"
    const val UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE"

    /** Someone else took the last seats between the list and the hold. */
    const val NO_SEATS_AVAILABLE = "NO_SEATS_AVAILABLE"

    const val DEPARTURE_NOT_OPEN = "DEPARTURE_NOT_OPEN"

    /** The hold expired before checkout completed — send the user back to date + party. */
    const val HOLD_EXPIRED = "HOLD_EXPIRED"

    const val PAYMENT_DECLINED = "PAYMENT_DECLINED"
    const val OTP_INVALID = "OTP_INVALID"
    const val OTP_RATE_LIMITED = "OTP_RATE_LIMITED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"

    // ---- Client-side only: never sent by the backend ----

    /** `success: false` with no code — should not happen, but must not crash. */
    const val UNKNOWN = "UNKNOWN"

    /**
     * The server answered with something that is not the contract's envelope —
     * a proxy's HTML 502, a captive portal, an empty body from a crashed process.
     */
    const val NON_JSON_RESPONSE = "NON_JSON_RESPONSE"
}
