package eg.bahr.core.catalog.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.network.ApiErrorCodes

/**
 * A 400 VALIDATION_FAILED: the server refused a key the request sent (a stale category or filter).
 * Home's filter and the category page both fall back from one to the full list.
 */
fun AppError.isValidationFailure(): Boolean = this is AppError.Api && code == ApiErrorCodes.VALIDATION_FAILED
