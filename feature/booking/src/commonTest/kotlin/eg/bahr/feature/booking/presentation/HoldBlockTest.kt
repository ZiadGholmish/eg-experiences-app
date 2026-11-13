package eg.bahr.feature.booking.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** "In 40 minutes" / "in 5 hours" / "in 3 days": always rounded up, so booking never opens later than said. */
class HoldBlockTest {
    private fun said(left: Duration) = left.toBlockTimeLeft().let { it.unit to it.count }

    @Test
    fun `under an hour is said in minutes`() {
        assertEquals(BlockUnit.MINUTES to 1, said(1.seconds))
        assertEquals(BlockUnit.MINUTES to 1, said(1.milliseconds))
        assertEquals(BlockUnit.MINUTES to 41, said(40.minutes + 1.seconds))
        assertEquals(BlockUnit.MINUTES to 59, said(59.minutes))
    }

    @Test
    fun `an hour up to a whole day is said in hours`() {
        assertEquals(BlockUnit.HOURS to 1, said(59.minutes + 1.seconds))
        assertEquals(BlockUnit.HOURS to 1, said(60.minutes))
        assertEquals(BlockUnit.HOURS to 5, said(4.hours + 1.minutes))
        assertEquals(BlockUnit.HOURS to 24, said(23.hours + 59.minutes))
        assertEquals(BlockUnit.HOURS to 24, said(24.hours), "the first block (24 h) reads in hours")
    }

    @Test
    fun `beyond a day is said in days`() {
        assertEquals(BlockUnit.DAYS to 2, said(24.hours + 1.seconds))
        assertEquals(BlockUnit.DAYS to 3, said(3.days))
        assertEquals(BlockUnit.DAYS to 7, said(7.days), "a repeat block")
        assertEquals(BlockUnit.DAYS to 30, said(30.days), "the longest manual block (MB-B1)")
    }
}
