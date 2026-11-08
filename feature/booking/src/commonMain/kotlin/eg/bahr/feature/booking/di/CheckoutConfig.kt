package eg.bahr.feature.booking.di

/**
 * Checkout settings only the app knows (its build config, M3-M1), bound by `:composeApp`'s entry points as they bind
 * [HoldReminders]; a graph without one sends no return link.
 *
 * [returnUrl] is the app's own link the hosted checkout sends the browser back to when the payment is done
 * (`<application id>://checkout`; the booking's reference is added under it). The server accepts only the prefixes
 * it lists (`PAYMENT_RETURN_URL_PREFIXES`), so a build whose server does not list it must leave it out (null): the
 * provider then shows its own page, and the app finds the outcome by reading the booking when it comes back.
 */
data class CheckoutConfig(
    val returnUrl: String? = null,
) {
    /** Where the checkout for [ref] returns to, or null when this build sends none. */
    fun returnUrlFor(ref: String): String? = returnUrl?.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/$ref" }
}
