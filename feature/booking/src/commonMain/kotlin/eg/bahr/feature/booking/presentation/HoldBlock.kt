package eg.bahr.feature.booking.presentation

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * `HOLD_BLOCKED` on the date + party step (MB-M1): too many of this install's, account's or phone's holds ended
 * unpaid, so the server takes no new hold for a while.
 *
 * [until] is on the device's clock: the answer's `retryAfter` added to the device's "now" when the answer arrived
 * (the contract counts it from then). Null when the server sent no `retryAfter`, which the contract does not allow
 * but an older server or a proxy might: the state then shows without a time and lasts while the screen is open.
 * [left] is what the screen says ("in 5 hours"), recomputed against [until] as the time passes.
 */
internal data class HoldBlock(
    val until: Instant?,
    val left: BlockTimeLeft?,
)

/** "In 40 minutes" / "in 5 hours" / "in 3 days": one unit and a whole count, rounded up. */
internal data class BlockTimeLeft(
    val unit: BlockUnit,
    val count: Int,
)

internal enum class BlockUnit { MINUTES, HOURS, DAYS }

/**
 * The unit the time left is said in, always rounded up, so the screen never says booking opens sooner than it does:
 * minutes under an hour, hours up to a whole day (a fresh 24 h block reads "in 24 hours", not "in 1 day"), days
 * beyond that (a 7-day block reads "in 7 days").
 */
internal fun Duration.toBlockTimeLeft(): BlockTimeLeft {
    val seconds = secondsLeftCeil().toLong()
    val minutes = ceilDiv(seconds, SECONDS_PER_MINUTE)
    val hours = ceilDiv(seconds, SECONDS_PER_HOUR)
    return when {
        minutes < MINUTES_PER_HOUR -> BlockTimeLeft(BlockUnit.MINUTES, minutes.coerceAtLeast(1L).toInt())
        hours <= HOURS_PER_DAY -> BlockTimeLeft(BlockUnit.HOURS, hours.toInt())
        else -> BlockTimeLeft(BlockUnit.DAYS, ceilDiv(seconds, SECONDS_PER_DAY).toInt())
    }
}

private fun ceilDiv(
    value: Long,
    divisor: Long,
): Long = (value + divisor - 1) / divisor

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
