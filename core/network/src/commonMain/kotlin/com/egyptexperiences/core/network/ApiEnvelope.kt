package com.egyptexperiences.core.network

import com.egyptexperiences.core.common.money.Money
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The one response envelope for the `/api/v1` contract.
 *
 * Mirrors `be/common/src/main/java/com/egyptexperiences/common/response/`.
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

/** Wire form of [Money]. */
@Serializable
data class MoneyDto(
    val amount: Double,
    @SerialName("currency") val currencyCode: String,
) {
    fun toMoney(): Money = Money(amount = amount, currency = currencyCode)
}
