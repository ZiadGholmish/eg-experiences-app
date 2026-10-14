package eg.bahr.core.common.phone

/**
 * The contract's `Phone` (`^\+?[0-9]{7,15}$` in openapi.yaml), for every phone field in the app: the
 * guest on a hold (feature:booking) and the account's own number. Moved here from feature:booking
 * (M2-M3 review #3) so the fields cannot drift apart. The server checks every number again and stays
 * the authority on it.
 *
 * The account's own number (M3-M0b, handoff §2: the first-sign-in sheet in feature:auth and Edit personal
 * info in feature:settings) takes one stricter rule on top, an Egyptian mobile: [normalizeMobile] first,
 * then [mobileProblem] / [isEgyptianMobile]. Every number that passes it also passes the contract's
 * [PATTERN] (11 Western digits), so the app never sends one the server would refuse.
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

    /** The handoff's Egyptian mobile (§2): 11 digits, `010`, `011`, `012` or `015` first. */
    val EGYPTIAN_MOBILE = Regex("^01[0125][0-9]{8}$")

    /** The length of an Egyptian mobile number, for the field's counter ("11/11"). */
    const val MOBILE_LENGTH = 11

    /**
     * The number as the server should get it, from what was typed: Arabic-Indic (٠–٩) and Eastern
     * Arabic-Indic (۰–۹) digits become Western ones; spaces, dashes and any other punctuation go (a
     * leading `+` is kept); the country code (`+20`, `0020`, or `20` in front of a full 10-digit number)
     * becomes the national `0`. So "+20 101 234 5678" and "٠١٠١٢٣٤٥٦٧٨" both become "01012345678".
     */
    fun normalizeMobile(typed: String): String {
        val western = typed.map(::westernDigit)
        val plus = western.firstOrNull { !it.isWhitespace() } == '+'
        val digits = western.filter { it in '0'..'9' }.joinToString("")
        return when {
            plus && digits.startsWith(COUNTRY_CODE) -> national(digits.removePrefix(COUNTRY_CODE))
            digits.startsWith(INTERNATIONAL_PREFIX + COUNTRY_CODE) -> national(digits.removePrefix(INTERNATIONAL_PREFIX + COUNTRY_CODE))
            digits.length == MOBILE_LENGTH + 1 && digits.startsWith(COUNTRY_CODE + "1") -> national(digits.removePrefix(COUNTRY_CODE))
            else -> digits
        }
    }

    /** The national form of what followed the country code; "+20 010…" (a 0 kept by habit) keeps one 0. */
    private fun national(afterCountryCode: String): String =
        if (afterCountryCode.startsWith(NATIONAL_PREFIX)) afterCountryCode else NATIONAL_PREFIX + afterCountryCode

    /** Whether [normalized] (see [normalizeMobile]) is an Egyptian mobile number. */
    fun isEgyptianMobile(normalized: String): Boolean = EGYPTIAN_MOBILE.matches(normalized)

    /** What is wrong with [normalized] as an Egyptian mobile number, or null when nothing is. */
    fun mobileProblem(normalized: String): MobileProblem? =
        when {
            isEgyptianMobile(normalized) -> null
            normalized.isEmpty() -> MobileProblem.Empty
            normalized.length < MOBILE_LENGTH -> MobileProblem.TooShort(normalized.length)
            normalized.length > MOBILE_LENGTH -> MobileProblem.TooLong(normalized.length)
            else -> MobileProblem.WrongStart
        }

    private fun westernDigit(c: Char): Char =
        when (c) {
            in ARABIC_INDIC_ZERO..(ARABIC_INDIC_ZERO + DIGITS - 1) -> '0' + (c - ARABIC_INDIC_ZERO)
            in EASTERN_ARABIC_INDIC_ZERO..(EASTERN_ARABIC_INDIC_ZERO + DIGITS - 1) -> '0' + (c - EASTERN_ARABIC_INDIC_ZERO)
            else -> c
        }

    private const val COUNTRY_CODE = "20"
    private const val INTERNATIONAL_PREFIX = "00"
    private const val NATIONAL_PREFIX = "0"
    private const val DIGITS = 10
    private const val ARABIC_INDIC_ZERO = '\u0660'
    private const val EASTERN_ARABIC_INDIC_ZERO = '\u06F0'
}

/** Why a number is not an Egyptian mobile (the field says which, handoff §2). */
sealed interface MobileProblem {
    data object Empty : MobileProblem

    data class TooShort(
        val digits: Int,
    ) : MobileProblem

    data class TooLong(
        val digits: Int,
    ) : MobileProblem

    /** 11 digits, but not `010`, `011`, `012` or `015` first. */
    data object WrongStart : MobileProblem
}
