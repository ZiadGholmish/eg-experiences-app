package eg.bahr.feature.booking.presentation

import eg.bahr.core.designsystem.format.BahrFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HoldCountdownTest {
    // The real server's format: an offset and five fraction digits (local api, 2026-10-09).
    private val expires = "2026-10-09T23:20:40.39219+03:00"
    private val serverNow = "2026-10-09T23:05:40.39219+03:00"

    @Test
    fun `parses the server's offset instants with fractions`() {
        assertEquals(Instant.parse("2026-10-09T20:20:40.39219Z"), HoldDeadline.parseInstant(expires))
    }

    @Test
    fun `a device clock that is wrong does not change the time left`() {
        // The phone thinks it is 09:00 UTC; the server says 20:05:40. Either way, 15 minutes are left.
        val deviceNow = Instant.parse("2026-10-09T09:00:00Z")
        val deadline = assertNotNull(HoldDeadline.from(expires, serverNow, deviceNow))

        assertEquals(15.minutes, deadline.remaining(deviceNow))
        assertEquals(10.minutes, deadline.remaining(deviceNow + 5.minutes))
    }

    @Test
    fun `time left never goes negative`() {
        val deviceNow = Instant.parse("2026-10-09T09:00:00Z")
        val deadline = assertNotNull(HoldDeadline.from(expires, serverNow, deviceNow))

        assertEquals(Duration.ZERO, deadline.remaining(deviceNow + 20.minutes))
    }

    @Test
    fun `an absent or garbled instant gives no deadline`() {
        val now = Instant.parse("2026-10-09T09:00:00Z")
        assertNull(HoldDeadline.from(null, serverNow, now))
        assertNull(HoldDeadline.from(expires, "not-a-timestamp", now))
        assertNull(HoldDeadline.length(expires, null))
    }

    @Test
    fun `the hold length is measured on the server's clock`() {
        assertEquals(15.minutes, HoldDeadline.length(expires, serverNow))
        // A deadline already passed when the server answered has no length to drain.
        assertNull(HoldDeadline.length(serverNow, expires))
    }

    @Test
    fun `seconds round up so zero shows only at the deadline`() {
        assertEquals(1, 1.milliseconds.secondsLeftCeil())
        assertEquals(0, Duration.ZERO.secondsLeftCeil())
        assertEquals(870, (14.minutes + 29.seconds + 1.milliseconds).secondsLeftCeil())
    }

    @Test
    fun `minutes round up for the spoken announcement`() {
        assertEquals(15, 15.minutes.minutesLeftCeil())
        assertEquals(14, (14.minutes).minutesLeftCeil())
        assertEquals(14, (13.minutes + 1.seconds).minutesLeftCeil())
        assertEquals(1, 1.seconds.minutesLeftCeil())
        assertEquals(0, Duration.ZERO.minutesLeftCeil())
    }

    @Test
    fun `formats as mm-ss with western digits`() {
        assertEquals("14:52", BahrFormat.countdown((14.minutes + 52.seconds).secondsLeftCeil()))
        assertEquals("00:00", BahrFormat.countdown(0))
    }
}
