package eg.bahr.feature.booking.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.booking.model.BookingDepartureDto
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.BookingPolicyDto
import eg.bahr.feature.booking.model.BookingTripDto
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
) : BookingRepository {
    val holdRequests = mutableListOf<PlaceHoldRequest>()
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
}
