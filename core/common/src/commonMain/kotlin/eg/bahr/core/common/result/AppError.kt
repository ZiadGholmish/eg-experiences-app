package eg.bahr.core.common.result

/**
 * A failure the UI can act on.
 *
 * [Api.code] is the backend's stable error code — the contract says the code is
 * what clients branch on, never the message (`ApiError` in `../docs/api/openapi.yaml`).
 * The known codes are `ApiErrorCodes` in `core:network`, the module that speaks the wire format.
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
