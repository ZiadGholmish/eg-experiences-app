package com.egyptexperiences.core.network

import com.egyptexperiences.core.common.result.ApiErrorCodes
import com.egyptexperiences.core.common.result.AppError
import com.egyptexperiences.core.common.result.AppResult
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.ContentConvertException
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Runs one API call and turns every outcome into an [AppResult].
 *
 * What this centralises, so no repository repeats it: unwrapping the
 * `{ success, data, error }` envelope, mapping transport failures to a typed
 * [AppError], letting `CancellationException` through so structured concurrency
 * still works, and treating `success: false` as a failure regardless of the
 * HTTP status.
 */
suspend inline fun <reified T> callApi(crossinline block: suspend () -> HttpResponse): AppResult<T> =
    try {
        val response = block()
        val envelope: ApiEnvelope<T>? = response.decodeEnvelopeOrNull()

        when {
            envelope == null ->
                // Not JSON at all: a proxy's HTML 502, a captive portal, an
                // empty body from a crashed server. There is no error code to
                // report, so the status is all the UI gets.
                AppResult.Failure(
                    AppError.Api(
                        code = ApiErrorCodes.NON_JSON_RESPONSE,
                        message = null,
                        httpStatus = response.status.value,
                    ),
                )

            envelope.success && envelope.data != null -> AppResult.Success(envelope.data)

            // A successful envelope with no body: `POST /bookings/{ref}/cancel`
            // answers `ApiResponse.ok(null)`. Only Unit callers can accept it.
            envelope.success && Unit is T -> AppResult.Success(Unit as T)

            else ->
                AppResult.Failure(
                    AppError.Api(
                        code = envelope.error?.code ?: ApiErrorCodes.UNKNOWN,
                        message = envelope.error?.message,
                        httpStatus = response.status.value,
                    ),
                )
        }
    } catch (cancellation: CancellationException) {
        // Never swallowed: a cancelled coroutine must stay cancelled.
        throw cancellation
    } catch (serialization: SerializationException) {
        // The body did not match the contract. Never retryable — it is a bug.
        AppResult.Failure(AppError.Serialization(serialization.message))
    } catch (unresolved: UnresolvedAddressException) {
        AppResult.Failure(AppError.Network)
    } catch (timeout: HttpRequestTimeoutException) {
        AppResult.Failure(AppError.Timeout)
    } catch (io: IOException) {
        AppResult.Failure(AppError.Network)
    }

/**
 * Reads the envelope, or null when the body is not the contract's JSON.
 *
 * Ktor wraps a `SerializationException` in a [ContentConvertException] and
 * raises [NoTransformationFoundException] when the body is not JSON at all;
 * neither is an `IOException`, so both would otherwise escape [callApi] and
 * crash the caller.
 */
suspend inline fun <reified T> HttpResponse.decodeEnvelopeOrNull(): ApiEnvelope<T>? =
    try {
        body<ApiEnvelope<T>>()
    } catch (noTransformation: NoTransformationFoundException) {
        null
    } catch (convert: ContentConvertException) {
        throw SerializationException(convert.message, convert)
    }
