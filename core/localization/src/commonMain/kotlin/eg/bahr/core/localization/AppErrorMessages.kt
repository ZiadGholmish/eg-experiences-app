package eg.bahr.core.localization

import androidx.compose.runtime.Composable
import eg.bahr.core.common.result.AppError
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.error_generic
import eg.bahr.core.localization.generated.resources.error_hold_expired
import eg.bahr.core.localization.generated.resources.error_network
import eg.bahr.core.localization.generated.resources.error_no_seats_available
import eg.bahr.core.localization.generated.resources.error_timeout
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One place that turns an [AppError] into something a person can read.
 *
 * Branches on the backend's error *code*, never its message — the contract says
 * the code is the stable part. An unmapped code falls back to the generic
 * string rather than showing the user a raw server message.
 *
 * Returns the resource rather than the text so non-composable callers (the
 * app-wide error host) can carry it and resolve it in the current locale.
 *
 * The codes are literals on purpose: their constants (`ApiErrorCodes`) live in
 * `core:network`, which this module may not depend on (bahr-modularization graph).
 * `ErrorCodeMessagesTest` in `feature:booking`, which sees both modules, fails if a
 * literal here drifts from its constant.
 */
fun AppError.messageRes(): StringResource =
    when (this) {
        AppError.Network -> Res.string.error_network
        AppError.Timeout -> Res.string.error_timeout
        is AppError.Api ->
            when (code) {
                HOLD_EXPIRED -> Res.string.error_hold_expired
                NO_SEATS_AVAILABLE -> Res.string.error_no_seats_available
                else -> Res.string.error_generic
            }
        is AppError.Serialization -> Res.string.error_generic
        is AppError.Unknown -> Res.string.error_generic
    }

// Mirrors core:network's ApiErrorCodes; see messageRes. Every literal added here needs a
// matching line in feature:booking's ErrorCodeMessagesTest, or it is not pinned.
private const val HOLD_EXPIRED = "HOLD_EXPIRED"
private const val NO_SEATS_AVAILABLE = "NO_SEATS_AVAILABLE"

/** [messageRes], resolved in the composition's language. */
@Composable
fun AppError.localizedMessage(): String = stringResource(messageRes())

/** 5xx is the server's problem and may pass; 4xx is this request's and will not. */
private const val SERVER_ERROR_FLOOR = 500

/** Retrying a contract mismatch just fails again; retrying a dead network does not. */
val AppError.isRetryable: Boolean
    get() =
        when (this) {
            AppError.Network, AppError.Timeout -> true
            is AppError.Api -> {
                val status = httpStatus
                status == null || status >= SERVER_ERROR_FLOOR
            }
            is AppError.Serialization -> false
            is AppError.Unknown -> true
        }
