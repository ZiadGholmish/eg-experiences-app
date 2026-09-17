package com.egyptexperiences.feature.booking.presentation

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * How long the hold has left.
 *
 * Measured against the server's `holdExpiresAt`, not a duration the client
 * started counting: a backgrounded app, a slow network and a wrong device clock
 * all make a client-side countdown lie, and this one is the difference between
 * a seat the user still has and a seat someone else already took.
 *
 * A device clock that is wrong by minutes will still mislead. That is why the
 * client re-reads the booking when the countdown hits zero instead of deciding
 * on its own that the hold is gone.
 */
object HoldCountdown {
    fun remaining(
        holdExpiresAt: String?,
        now: Instant = Clock.System.now(),
    ): Duration? {
        val expiry = holdExpiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val left = expiry - now
        return if (left.isNegative()) Duration.ZERO else left
    }

    /** `m:ss`, the form the canvas's checkout timer uses. */
    fun format(remaining: Duration): String {
        val totalSeconds = remaining.inWholeSeconds
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
