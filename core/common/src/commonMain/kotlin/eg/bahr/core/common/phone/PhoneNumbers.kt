package eg.bahr.core.common.phone

/**
 * The contract's `Phone` (`^\+?[0-9]{7,15}$` in openapi.yaml), for every phone field in the app: the
 * guest on a hold (feature:booking) and the account's own number (feature:auth, M3-M0). Moved here from
 * feature:booking (M2-M3 review #3) so the two fields cannot drift apart. No business meaning: only the
 * wire format's rule, which the server checks again and stays the authority on.
 */
object PhoneNumbers {
    /** The longest value [PATTERN] accepts: a `+` and 15 digits. */
    const val MAX_LENGTH = 16

    /** openapi `Phone`. */
    val PATTERN = Regex("""^\+?[0-9]{7,15}$""")

    /**
     * Keeps digits and a leading `+` only, so "010 1234 5678" and "+20 10…" both become what [PATTERN]
     * accepts, cut at [MAX_LENGTH]. The server compares phones by their digits.
     */
    fun clean(typed: String): String {
        val digits = typed.filter { it.isDigit() }
        return (if (typed.trimStart().startsWith('+')) "+$digits" else digits).take(MAX_LENGTH)
    }

    fun isValid(phone: String): Boolean = PATTERN.matches(phone)
}
