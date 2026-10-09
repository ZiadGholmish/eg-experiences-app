package eg.bahr.feature.booking.presentation

import eg.bahr.feature.booking.model.BookingDepartureDto

/**
 * Whether a date can be booked and, if not, why (product decision D3); each reason is drawn
 * differently on the date row. Only [Open] can be picked on this screen: the sold-out notice and
 * waitlist live on the trip page (M2-M3).
 *
 * Read from the contract's `unavailableReason`, never from `soldOut`: a cancelled date that was also
 * full has `soldOut: true` but reads [Cancelled]. `feature:trips` has its own copy of this mapping
 * because features do not share code; it is four lines on a wire string, not a type worth a core module.
 */
internal enum class DateAvailability { Open, SoldOut, Cancelled, Closed }

internal val BookingDepartureDto.availability: DateAvailability
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

internal val BookingDepartureDto.isOpen: Boolean
    get() = availability == DateAvailability.Open

private const val REASON_SOLD_OUT = "SOLD_OUT"
private const val REASON_CANCELLED = "CANCELLED"
