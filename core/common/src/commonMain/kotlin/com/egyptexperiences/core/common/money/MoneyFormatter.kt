package com.egyptexperiences.core.common.money

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats [Money] for display.
 *
 * Prices in this product are whole pounds (the canvas shows `450 EGP`), so the
 * fractional part is dropped when it is zero rather than padding every price
 * with `.00`. Grouping is inserted manually because `kotlinx-datetime`-style
 * locale-aware number formatting has no common-code equivalent.
 */
object MoneyFormatter {
    fun format(
        money: Money,
        currencyLabel: String = money.currency,
    ): String = "${groupedAmount(money.amount)} $currencyLabel"

    private fun groupedAmount(amount: Double): String {
        val negative = amount < 0
        val absolute = abs(amount)
        val whole = absolute.toLong()
        val fraction = ((absolute - whole) * 100).roundToLong()

        val grouped =
            whole
                .toString()
                .reversed()
                .chunked(3)
                .joinToString(",")
                .reversed()

        val body = if (fraction == 0L) grouped else "$grouped.${fraction.toString().padStart(2, '0')}"
        return if (negative) "-$body" else body
    }
}
