package eg.bahr.core.designsystem.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Carries over the old `MoneyFormatterTest` and the `m:ss` cases of `HoldCountdownTest`, adapted
 * to the handoff format: whole-EGP `Long` amounts (openapi `Money.amount` is int64) and `mm:ss`.
 */
class BahrFormatTest {
    @Test
    fun `whole pounds carry no decimal part`() {
        // The design shows "450 EGP", not "450.00 EGP".
        assertEquals("450 EGP", BahrFormat.egp(450, arabic = false))
    }

    @Test
    fun `thousands are grouped`() {
        assertEquals("6,300 EGP", BahrFormat.egp(6_300, arabic = false))
        assertEquals("1,234,567 EGP", BahrFormat.egp(1_234_567, arabic = false))
    }

    @Test
    fun `amounts beyond the Int range are grouped too`() {
        // The contract's amount is int64; the handoff's Int signature would have overflowed here.
        assertEquals("3,000,000,000 EGP", BahrFormat.egp(3_000_000_000, arabic = false))
    }

    @Test
    fun `a refund reads as negative`() {
        assertEquals("-450 EGP", BahrFormat.egp(-450, arabic = false))
        // The sign is not part of a digit group: never "-,450".
        assertEquals("-1,450 EGP", BahrFormat.egp(-1_450, arabic = false))
    }

    @Test
    fun `arabic uses the localized currency symbol with western digits`() {
        assertEquals("450 ج.م", BahrFormat.egp(450, arabic = true))
        assertEquals("6,300 ج.م", BahrFormat.money(6_300, "EGP", arabic = true))
    }

    @Test
    fun `a currency other than EGP shows its code instead of a guessed symbol`() {
        assertEquals("450 USD", BahrFormat.money(450, "USD", arabic = true))
    }

    @Test
    fun `times are 24 hour and zero padded`() {
        assertEquals("05:00", BahrFormat.time(LocalTime(5, 0)))
        assertEquals("22:07", BahrFormat.time(LocalTime(22, 7)))
    }

    @Test
    fun `a time range is isolated left-to-right so RTL text cannot reverse it`() {
        assertEquals("\u206605:00 → 22:00\u2069", BahrFormat.timeRange(LocalTime(5, 0), LocalTime(22, 0)))
    }

    @Test
    fun `countdown formats as zero padded minutes and seconds`() {
        assertEquals("14:59", BahrFormat.countdown(14 * 60 + 59))
        assertEquals("09:30", BahrFormat.countdown(9 * 60 + 30))
        assertEquals("00:05", BahrFormat.countdown(5))
        assertEquals("00:00", BahrFormat.countdown(0))
    }

    @Test
    fun `a negative countdown clamps to zero`() {
        // A device clock running fast must never show "-1:59".
        assertEquals("00:00", BahrFormat.countdown(-119))
    }

    @Test
    fun `dates use western digits in both languages`() {
        val date = LocalDate(2026, 10, 17) // a Saturday
        assertEquals("Sat 17 Oct", BahrFormat.date(date, arabic = false))
        assertEquals("السبت 17 أكتوبر", BahrFormat.date(date, arabic = true))
    }

    @Test
    fun `booking reference alphabet has no look-alike characters`() {
        listOf('0', 'O', '1', 'I').forEach { assertTrue(it !in BahrFormat.REF_ALPHABET) }
    }
}
