package com.egyptexperiences.core.common.money

/**
 * An amount with its currency, never a bare number.
 *
 * The backend sends `{ amount, currency }` for every price for the reason its
 * `MoneyResponse` javadoc gives: a number without a currency forces each client
 * to assume one. Phase 1 is EGP-only, and this type is what makes a second
 * currency a feature rather than a bug.
 *
 * The client never does money arithmetic. Totals, refunds and commission are
 * computed server-side against a ledger; here [amount] is only ever formatted.
 */
data class Money(
    val amount: Double,
    val currency: String,
) {
    companion object {
        const val EGP = "EGP"
    }
}
