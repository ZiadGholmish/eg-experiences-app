package eg.bahr.core.designsystem.format

import androidx.compose.runtime.Composable
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.months_short
import eg.bahr.core.localization.generated.resources.weekdays_short
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringArrayResource
import kotlin.math.round

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

    /**
     * "Sat 17 Oct" / "السبت 17 أكتوبر". The day and month words come from `core:localization`
     * (`weekdays_short`, `months_short`), so the composition's language picks them; the digits are
     * Kotlin's, so they stay Western in Arabic.
     */
    @Composable
    fun date(d: LocalDate): String = date(d, dateNames())

    /** "Sat" / "السبت": the first line of a date card. */
    @Composable
    fun weekday(d: LocalDate): String = weekday(d, dateNames())

    /** "17 Oct" / "17 أكتوبر": the second line of a date card. */
    @Composable
    fun dayMonth(d: LocalDate): String = dayMonth(d, dateNames())

    /** "21 Sep 2026" / "21 سبتمبر 2026": a review's date. */
    @Composable
    fun dayMonthYear(d: LocalDate): String = dayMonthYear(d, dateNames())

    // Pure cores, so the arithmetic is tested without a composition. Both languages put the parts in
    // the same order (weekday, day, month, year), so the order lives here and only the words are
    // translated. Until the arrays load (compose-resources reads them asynchronously on some
    // platforms) the names are empty, and the ISO date is shown rather than an index failure.
    internal fun date(
        d: LocalDate,
        names: DateNames,
    ): String = if (names.isComplete) "${weekday(d, names)} ${dayMonth(d, names)}" else d.toString()

    internal fun weekday(
        d: LocalDate,
        names: DateNames,
    ): String = names.weekdays.getOrNull(d.dayOfWeek.ordinal) ?: d.toString()

    internal fun dayMonth(
        d: LocalDate,
        names: DateNames,
    ): String = names.months.getOrNull(d.monthNumber - 1)?.let { "${d.dayOfMonth} $it" } ?: d.toString()

    internal fun dayMonthYear(
        d: LocalDate,
        names: DateNames,
    ): String = if (names.isComplete) "${dayMonth(d, names)} ${d.year}" else d.toString()

    /** Weekday names Monday first (ISO order), month names January first. */
    internal data class DateNames(
        val weekdays: List<String>,
        val months: List<String>,
    ) {
        val isComplete get() = weekdays.size == DAYS_IN_WEEK && months.size == MONTHS_IN_YEAR
    }

    @Composable
    private fun dateNames() =
        DateNames(
            weekdays = stringArrayResource(Res.array.weekdays_short),
            months = stringArrayResource(Res.array.months_short),
        )

    private const val DAYS_IN_WEEK = 7
    private const val MONTHS_IN_YEAR = 12

    /**
     * A review average with one decimal: `4.8`, `5.0`. Hand-rolled, like [money], because a platform
     * formatter in Arabic would print `٤٫٨`.
     */
    fun rating(value: Double): String {
        val tenths = round(value * TENTHS).toLong()
        return "${tenths / TENTHS}.${tenths % TENTHS}"
    }

    private const val TENTHS = 10

    /**
     * Wraps server text that must read left to right inside an Arabic line, e.g. the trip's
     * `durationLabel` "05:00 → 22:00": the same isolates as [timeRange], for times that arrive
     * already formatted.
     */
    fun ltr(text: String): String = "$LTR_ISOLATE$text$POP_ISOLATE"

    /** Booking reference, e.g. BHR-7K4Q. Uppercase, no 0/O or 1/I so it survives being read aloud. */
    const val REF_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
}
