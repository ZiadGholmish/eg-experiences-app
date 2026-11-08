package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.account.PersonName
import eg.bahr.core.common.phone.PhoneNumbers

/**
 * The two ways to pay (HANDOFF screen 5: "These are the only two methods"), Vodafone Cash first and picked by default.
 * [wire] is openapi `StartPaymentRequest.method`.
 */
internal enum class PaymentMethod(
    val wire: String,
) {
    VodafoneCash("vodafone_cash"),
    Card("card"),
}

/** Where the payment stands on the held-seats screen (M3-M1). */
internal enum class PaymentStep {
    /** Picking a method and filling the payer's fields; the pay button is live. */
    Choosing,

    /** `startPayment` is on its way. */
    Starting,

    /** The hosted checkout was opened (`REDIRECT`); the booking is polled until the provider's answer lands. */
    AwaitingCheckout,

    /** Nothing to open (`WALLET_PENDING`): the provider asks on the phone; polled the same way. */
    AwaitingWallet,

    /** PAYMENT_PENDING found on a read rather than started here (a reopen, after process death): polled. */
    Confirming,

    /**
     * The booking read EXPIRED after a payment was started: the money may still arrive and confirm it, or be refunded
     * automatically (openapi `getBooking`). Still polled, more slowly, until it turns CONFIRMED or the person leaves.
     */
    Late,
    ;

    /** A payment is under way: the seats cannot simply be released any more (`releaseHold` answers CONFLICT). */
    val isUnderWay: Boolean get() = this == AwaitingCheckout || this == AwaitingWallet || this == Confirming || this == Late
}

/**
 * The payer's fields as typed, and what is sent from them. The same two values serve both methods (the labels change:
 * "Full name" + "Vodafone Cash number", or "Name on card" + "Mobile number"). Card details are never asked here: the
 * provider's hosted checkout takes them (PLAN §9, no card data on our servers).
 */
internal data class PayerForm(
    val name: String = "",
    val phone: String = "",
) {
    val cleanName: String get() = PersonName.clean(name)

    /** The number as the server gets it: Western digits, national form (`01012345678`). */
    val cleanPhone: String get() = PhoneNumbers.normalizeMobile(phone)

    val nameValid: Boolean get() = PersonName.isValid(name)

    /** An Egyptian mobile: Vodafone Cash numbers are, and the payment form's phone is one too. */
    val phoneValid: Boolean get() = PhoneNumbers.isEgyptianMobile(cleanPhone)

    val isValid: Boolean get() = nameValid && phoneValid

    companion object {
        /** The hold's own phone, if it is a mobile, as the form's first number: usually the one that pays. */
        fun prefilled(guestPhone: String): PayerForm =
            PayerForm(phone = PhoneNumbers.normalizeMobile(guestPhone).takeIf(PhoneNumbers::isEgyptianMobile).orEmpty())
    }
}
