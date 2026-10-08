package eg.bahr.core.designsystem.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Product rules: prices in EGP, 24-hour times, Western digits (0-9) in BOTH languages.
 * Never use platform number/date formatters with the ar locale — they emit ٠١٢٣.
 *
 * The app's only formatter. Money arrives as the contract's `{ amount: int64, currency }` in whole
 * pounds; the client never does arithmetic on it, so these functions only format.
 */
object BahrFormat {
    /** `Money.currency` in openapi.yaml is `const: EGP` today. */
    const val EGP = "EGP"
    private const val EGP_ARABIC = "ج.م"

    private const val DIGIT_GROUP = 3
    private const val SECONDS_PER_MINUTE = 60
    private const val TWO_DIGITS = 2

    /** U+2066 LEFT-TO-RIGHT ISOLATE … U+2069 POP DIRECTIONAL ISOLATE. */
    internal const val LTR_ISOLATE = "\u2066"
    internal const val POP_ISOLATE = "\u2069"

    /** `450 EGP` / `450 ج.م`; `6,300 EGP`. */
    fun egp(
        amount: Long,
        arabic: Boolean,
    ): String = money(amount, EGP, arabic)

    /**
     * Formats a contract `Money`. A currency other than EGP is shown by its code rather than
     * guessed at, so a second currency is visible instead of mislabelled.
     */
    fun money(
        amount: Long,
        currency: String,
        arabic: Boolean,
    ): String {
        val label =
            when {
                currency != EGP -> currency
                arabic -> EGP_ARABIC
                else -> EGP
            }
        return "${grouped(amount)} $label"
    }

    // Group the digits, not the sign: `-450` must not become `-,450`.
    private fun grouped(amount: Long): String {
        val digits = amount.toString().removePrefix("-")
        val grouped =
            digits
                .reversed()
                .chunked(DIGIT_GROUP)
                .joinToString(",")
                .reversed()
        return if (amount < 0) "-$grouped" else grouped
    }

    fun time(t: LocalTime): String = "${t.hour.twoDigits()}:${t.minute.twoDigits()}"

    /**
     * `05:00 → 22:00`, wrapped in a left-to-right isolate. Inside an Arabic (RTL) line the bidi
     * algorithm would otherwise lay the two times out as `22:00 → 05:00` — times stay LTR in RTL.
     */
    fun timeRange(
        from: LocalTime,
        to: LocalTime,
    ) = "$LTR_ISOLATE${time(from)} → ${time(to)}$POP_ISOLATE"

    /** Countdown for the 15-minute seat hold: `14:59`. Negative input clamps to `00:00`. */
    fun countdown(secondsLeft: Int): String {
        val s = secondsLeft.coerceAtLeast(0)
        return "${(s / SECONDS_PER_MINUTE).twoDigits()}:${(s % SECONDS_PER_MINUTE).twoDigits()}"
    }

    private fun Int.twoDigits() = toString().padStart(TWO_DIGITS, '0')

    private val daysEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val daysAr = listOf("الاتنين", "التلات", "الأربع", "الخميس", "الجمعة", "السبت", "الحد")
    private val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val monthsAr =
        listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

    /** "Sat 17 Oct" / "السبت 17 أكتوبر" */
    fun date(
        d: LocalDate,
        arabic: Boolean,
    ): String {
        val i = d.dayOfWeek.ordinal
        val m = d.monthNumber - 1
        return if (arabic) "${daysAr[i]} ${d.dayOfMonth} ${monthsAr[m]}" else "${daysEn[i]} ${d.dayOfMonth} ${monthsEn[m]}"
    }

    /** Booking reference, e.g. BHR-7K4Q. Uppercase, no 0/O or 1/I so it survives being read aloud. */
    const val REF_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
}
