package eg.bahr.feature.trips.presentation

import eg.bahr.feature.trips.model.DepartureDto

/**
 * Whether a date can be booked and, if not, why (product decision D3). Each reason is drawn
 * differently on the date card ("Sold out" / "Cancelled" / "Booking closed").
 *
 * Read from the contract's `unavailableReason`, never from `soldOut`: a cancelled date that was
 * also full has `soldOut: true` but reads [Cancelled], and a full date past its cutoff reads
 * [Closed]. Only [SoldOut] can be picked to show the sold-out notice (and, in M2-M3, the waitlist).
 */
internal enum class DateAvailability { Open, SoldOut, Cancelled, Closed }

internal val DepartureDto.availability: DateAvailability
    get() =
        when (unavailableReason) {
            // The contract keeps `bookable` and an absent reason in step; if they ever disagree, the
            // date is not offered, because the hold would refuse it anyway.
            null -> if (bookable) DateAvailability.Open else DateAvailability.Closed
            REASON_SOLD_OUT -> DateAvailability.SoldOut
            REASON_CANCELLED -> DateAvailability.Cancelled
            // CLOSED, and any reason this build does not know yet: not bookable, said as "closed".
            else -> DateAvailability.Closed
        }

private const val REASON_SOLD_OUT = "SOLD_OUT"
private const val REASON_CANCELLED = "CANCELLED"
