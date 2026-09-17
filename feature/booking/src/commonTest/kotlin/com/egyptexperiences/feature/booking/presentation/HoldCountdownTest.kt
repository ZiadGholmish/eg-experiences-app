package com.egyptexperiences.feature.booking.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HoldCountdownTest {
    private val now = Instant.parse("2026-05-01T10:00:00Z")

    @Test
    fun `reports the time left against the server expiry`() {
        val remaining = HoldCountdown.remaining("2026-05-01T10:09:30Z", now)

        assertEquals(9.minutes + 30.seconds, remaining)
    }

    @Test
    fun `an expiry already in the past is zero - never negative`() {
        // A device clock running fast must not produce a negative countdown
        // that formats as "-1:59".
        val remaining = HoldCountdown.remaining("2026-05-01T09:58:00Z", now)

        assertEquals(Duration.ZERO, remaining)
    }

    @Test
    fun `a booking with no hold has no countdown`() {
        assertNull(HoldCountdown.remaining(null, now))
    }

    @Test
    fun `an unparseable expiry is treated as no countdown - not as expired`() {
        // Showing "0:00" for a timestamp we failed to read would tell the user
        // their seats are gone when they are not.
        assertNull(HoldCountdown.remaining("not-a-timestamp", now))
    }

    @Test
    fun `formats as minutes and zero-padded seconds`() {
        assertEquals("9:30", HoldCountdown.format(9.minutes + 30.seconds))
        assertEquals("0:05", HoldCountdown.format(5.seconds))
        assertEquals("0:00", HoldCountdown.format(Duration.ZERO))
    }
}
