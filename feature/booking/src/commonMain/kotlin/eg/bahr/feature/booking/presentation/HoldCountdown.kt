package eg.bahr.feature.booking.presentation

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The hold's deadline on the device's clock.
 *
 * The server's `holdExpiresAt` is on the server's clock, and a phone's clock can be minutes off.
 * Each server answer carries `serverNow` too, so the time the hold has left *by the server's own
 * clock* is `holdExpiresAt − serverNow`; adding that to the device's "now" at the moment the answer
 * arrived gives a deadline the device can count against without trusting its own wall time.
 *
 * It is only as fresh as the answer it came from: a deadline built from an old answer (a route
 * restored after process death, a phone that slept) is wrong by however long ago that was. That is
 * why the screen re-reads `GET /bookings/{ref}` every time it comes back, and again at zero.
 */
internal data class HoldDeadline(
    val onDevice: Instant,
) {
    /** What is left at device time [now]; never negative. */
    fun remaining(now: Instant): Duration = (onDevice - now).coerceAtLeast(Duration.ZERO)

    companion object {
        /**
         * The deadline from one server answer, received at device time [receivedAt]. Null when
         * either instant does not parse.
         */
        fun from(
            holdExpiresAt: String?,
            serverNow: String?,
            receivedAt: Instant,
        ): HoldDeadline? {
            val expires = parseInstant(holdExpiresAt) ?: return null
            val serverClock = parseInstant(serverNow) ?: return null
            return HoldDeadline(receivedAt + (expires - serverClock))
        }

        /** The hold's full length by the server's clock: the progress bar's 100 %. */
        fun length(
            holdExpiresAt: String?,
            serverNow: String?,
        ): Duration? {
            val expires = parseInstant(holdExpiresAt) ?: return null
            val serverClock = parseInstant(serverNow) ?: return null
            return (expires - serverClock).takeIf { it.isPositive() }
        }

        /** The contract's instants carry an offset (`+02:00` / `+03:00`) and up to 6 fraction digits. */
        fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
}

/** Whole seconds left, rounded up, so `00:00` shows only once the time is really up. */
internal fun Duration.secondsLeftCeil(): Int = ((inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

/** Whole minutes left, rounded up: "14 minutes left" from 14:00 down to 13:01. */
internal fun Duration.minutesLeftCeil(): Int = ((secondsLeftCeil() + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE)

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60
