package eg.bahr.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The one response envelope for the `/api/v1` contract.
 *
 * Mirrors the `*Envelope` and `ApiError` schemas in `../docs/api/openapi.yaml`.
 * `success` is read instead of the HTTP status on purpose — the backend's own
 * comment says it exists so the KMP client does not have to reason about status
 * handling that differs per platform.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String? = null,
    val violations: List<FieldViolationDto>? = null,
)

@Serializable
data class FieldViolationDto(
    val field: String,
    val message: String,
)

/**
 * A page of results.
 *
 * Deliberately not Spring's `Page` shape — see the backend's `PageResponse`.
 */
@Serializable
data class PageDto<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
) {
    val hasMore: Boolean get() = page + 1 < totalPages
}

/**
 * The contract's `Money`: an amount with its currency, never a bare number.
 *
 * A number without a currency forces each client to assume one; phase 1 is EGP-only, and carrying
 * the currency is what makes a second one a feature rather than a bug. [amount] is whole pounds
 * (`int64` in openapi.yaml). The client never does money arithmetic — totals, refunds and
 * commission come from the server — so this is only ever formatted, by `BahrFormat.money`.
 */
@Serializable
data class MoneyDto(
    val amount: Long,
    @SerialName("currency") val currencyCode: String,
)
