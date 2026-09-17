package com.egyptexperiences.core.localization

import androidx.compose.runtime.Composable
import com.egyptexperiences.core.common.result.ApiErrorCodes
import com.egyptexperiences.core.common.result.AppError
import com.egyptexperiences.core.localization.generated.resources.Res
import com.egyptexperiences.core.localization.generated.resources.error_generic
import com.egyptexperiences.core.localization.generated.resources.error_hold_expired
import com.egyptexperiences.core.localization.generated.resources.error_network
import com.egyptexperiences.core.localization.generated.resources.error_seats_unavailable
import com.egyptexperiences.core.localization.generated.resources.error_timeout
import org.jetbrains.compose.resources.stringResource

/**
 * One place that turns an [AppError] into something a person can read.
 *
 * Branches on the backend's error *code*, never its message — the contract says
 * the code is the stable part. An unmapped code falls back to the generic
 * string rather than showing the user a raw server message.
 */
@Composable
fun AppError.localizedMessage(): String =
    when (this) {
        AppError.Network -> stringResource(Res.string.error_network)
        AppError.Timeout -> stringResource(Res.string.error_timeout)
        is AppError.Api ->
            when (code) {
                ApiErrorCodes.HOLD_EXPIRED -> stringResource(Res.string.error_hold_expired)
                ApiErrorCodes.SEATS_UNAVAILABLE -> stringResource(Res.string.error_seats_unavailable)
                else -> stringResource(Res.string.error_generic)
            }
        is AppError.Serialization -> stringResource(Res.string.error_generic)
        is AppError.Unknown -> stringResource(Res.string.error_generic)
    }

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
