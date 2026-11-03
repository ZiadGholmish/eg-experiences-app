package eg.bahr.feature.mytrips.model

import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.network.MoneyDto
import kotlinx.serialization.Serializable

/**
 * openapi `Booking`, as `listMyBookings` serves it: only the fields My trips shows. A partial copy on
 * purpose (as feature:booking's `HeldBookingDto`); the parser ignores the rest (`host`, `calendar`, …).
 */
@Serializable
internal data class MyBookingDto(
    val ref: String,
    val status: String,
    val date: String,
    val partySize: Int,
    val total: MoneyDto? = null,
    val dayLabel: String? = null,
    val holdExpiresAt: String? = null,
    val trip: MyTripDto? = null,
    val departure: MyDepartureDto? = null,
    /** Since M3-B7, `listMyBookings` only: the booking may be reviewed now ("Rate your trip", M3-M4). */
    val canReview: Boolean = false,
    /** Since M3-B7, `listMyBookings` only: the review written for this booking, if any. */
    val review: MyBookingReviewDto? = null,
)

/** openapi `Booking.review` (M3-B7): the booking's review and where moderation has it. */
@Serializable
internal data class MyBookingReviewDto(
    val id: String,
    val status: String,
    val canEdit: Boolean = false,
)

/** openapi `MyReviewStatus`. The contract says to read an unknown value as [Pending]. */
internal enum class ReviewStatus {
    Pending,
    Published,
    Rejected,
    ;

    companion object {
        fun of(wire: String): ReviewStatus =
            when (wire) {
                "published" -> Published
                "rejected" -> Rejected
                else -> Pending
            }
    }
}

/** openapi `Booking.trip` / `MyWaitlistEntry.trip`. */
@Serializable
internal data class MyTripDto(
    val slug: String? = null,
    val title: String? = null,
    val cardImage: ImageDto? = null,
)

/** openapi `Booking.departure`: only the time is shown here. */
@Serializable
internal data class MyDepartureDto(
    val timeLocal: String? = null,
)

/** openapi `MyWaitlistEntry`. [seatsRemaining] is a display hint; the hold decides. */
@Serializable
internal data class MyWaitlistEntryDto(
    val id: String,
    val departureId: String,
    val date: String,
    val partySize: Int,
    val dayLabel: String? = null,
    val departTime: String? = null,
    val locale: String? = null,
    val joinedAt: String? = null,
    val seatsRemaining: Int = 0,
    val soldOut: Boolean = true,
    val bookable: Boolean = false,
    val unavailableReason: String? = null,
    val trip: MyTripDto? = null,
)

/** openapi `Booking.status`. An unknown value (a later server) reads as [Other]. */
internal enum class BookingStatus {
    Held,
    PaymentPending,
    Confirmed,
    Completed,
    Cancelled,
    Expired,
    Other,
    ;

    companion object {
        fun of(wire: String): BookingStatus =
            when (wire) {
                "HELD" -> Held
                "PAYMENT_PENDING" -> PaymentPending
                "CONFIRMED" -> Confirmed
                "COMPLETED" -> Completed
                "CANCELLED" -> Cancelled
                "EXPIRED" -> Expired
                else -> Other
            }
    }
}
