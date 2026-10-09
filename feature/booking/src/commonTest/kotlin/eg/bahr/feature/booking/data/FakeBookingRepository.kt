package eg.bahr.feature.booking.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.booking.model.BookingDepartureDto
import eg.bahr.feature.booking.model.BookingDeparturePlaceDto
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.BookingPolicyDto
import eg.bahr.feature.booking.model.BookingTripDto
import eg.bahr.feature.booking.model.BookingTripSummaryDto
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.model.HeldSeatsDto
import eg.bahr.feature.booking.model.PlaceHoldRequest
import kotlinx.datetime.LocalDate

/**
 * Hand-written fake: each call answers from a lambda the test sets, and every hold request and
 * departures read is recorded. Unset calls fail loudly with a generic error.
 */
internal class FakeBookingRepository(
    var tripBySlug: suspend (slug: String) -> AppResult<BookingTripDto> = { unset() },
    var departuresFor: suspend (slug: String) -> AppResult<List<BookingDepartureDto>> = { unset() },
    var placeHold: suspend (request: PlaceHoldRequest) -> AppResult<HeldSeatsDto> = { unset() },
    var heldBooking: suspend (ref: String, phone: String) -> AppResult<HeldBookingDto> = { _, _ -> unset() },
    var releaseHold: suspend (ref: String, phone: String) -> AppResult<Unit> = { _, _ -> unset() },
) : BookingRepository {
    val holdRequests = mutableListOf<PlaceHoldRequest>()

    /** `ref to phone` of every booking read and every release, in order. */
    val bookingReads = mutableListOf<Pair<String, String>>()
    val releases = mutableListOf<Pair<String, String>>()
    var departureReads = 0
        private set

    override suspend fun tripBySlug(slug: String): AppResult<BookingTripDto> = tripBySlug.invoke(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<BookingDepartureDto>> {
        departureReads++
        return departuresFor.invoke(slug)
    }

    override suspend fun placeHold(request: PlaceHoldRequest): AppResult<HeldSeatsDto> {
        holdRequests += request
        return placeHold.invoke(request)
    }

    override suspend fun bookingByRef(
        ref: String,
        phone: String?,
    ): AppResult<BookingDto> = unset()

    override suspend fun heldBooking(
        ref: String,
        phone: String,
    ): AppResult<HeldBookingDto> {
        bookingReads += ref to phone
        return heldBooking.invoke(ref, phone)
    }

    override suspend fun releaseHold(
        ref: String,
        phone: String,
    ): AppResult<Unit> {
        releases += ref to phone
        return releaseHold.invoke(ref, phone)
    }

    private companion object {
        fun unset(): AppResult.Failure = AppResult.Failure(AppError.Unknown("FakeBookingRepository: call not stubbed"))
    }
}

/**
 * The seeded dawn trip as this screen reads it: four Saturdays with 6, 2, 0 (sold out) and 11 seats
 * left of 18, 450 EGP, at most 6 per booking. Copy is Arabic because Arabic is what most users see.
 */
internal object BookingFixtures {
    const val SLUG = "burullus-dawn"
    val PRICE = MoneyDto(amount = 450, currencyCode = "EGP")

    fun saturdays(): List<BookingDepartureDto> =
        listOf(6 to "2026-10-10", 2 to "2026-10-17", 0 to "2026-10-24", 11 to "2026-10-31").mapIndexed { i, (left, date) ->
            BookingDepartureDto(
                id = "dep-${i + 1}",
                date = LocalDate.parse(date),
                departTime = "05:00",
                returnTime = "22:00",
                seatsRemaining = left,
                capacity = 18,
                soldOut = left == 0,
                bookable = left > 0,
                unavailableReason = if (left == 0) "SOLD_OUT" else null,
                price = PRICE,
            )
        }

    fun trip(
        title: String = "الفجر على بحيرة البرلس",
        maxPartySize: Int? = 6,
        dates: List<BookingDepartureDto> = saturdays(),
    ) = BookingTripDto(
        slug = SLUG,
        title = title,
        durationLabel = "05:00 → 22:00",
        price = PRICE,
        dates = dates,
        policy = BookingPolicyDto(freeCancellationHours = 72, maxPartySize = maxPartySize),
    )

    /** openapi `HeldSeats` example values. */
    fun held(partySize: Int = 2) =
        HeldSeatsDto(
            ref = "BRL-7K4M2X9P",
            departureId = "dep-1",
            partySize = partySize,
            pricePerPerson = PRICE,
            total = MoneyDto(amount = 900, currencyCode = "EGP"),
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
            seatsRemaining = 4,
        )

    /**
     * `GET /bookings/{ref}` for the hold above, as the local api answers it: [status] HELD with the
     * deadline, or (anything else) without one. Times are the server's, with their +03:00 offset.
     */
    fun heldBooking(
        status: String = "HELD",
        holdExpiresAt: String? = "2026-10-09T22:42:30+03:00",
        serverNow: String = "2026-10-09T22:27:30+03:00",
        title: String = "الفجر على بحيرة البرلس",
        dayLabel: String = "السبت 10 أكتوبر",
        city: String = "القاهرة",
        placeName: String = "موقف عبد المنعم رياض",
    ) = HeldBookingDto(
        ref = "BRL-7K4M2X9P",
        status = status,
        holdExpiresAt = holdExpiresAt.takeIf { status == "HELD" || status == "PAYMENT_PENDING" },
        serverNow = serverNow,
        trip = BookingTripSummaryDto(slug = SLUG, title = title),
        date = LocalDate.parse("2026-10-10"),
        dayLabel = dayLabel,
        departure = BookingDeparturePlaceDto(placeName = placeName, city = city, timeLocal = "05:00", arriveBy = "04:45"),
        returnTime = "22:00",
        partySize = 2,
        total = MoneyDto(amount = 900, currencyCode = "EGP"),
    )
}
