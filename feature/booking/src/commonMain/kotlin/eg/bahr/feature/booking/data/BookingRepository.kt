package eg.bahr.feature.booking.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.booking.model.BookingDepartureDto
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.BookingTripDto
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.model.HeldSeatsDto
import eg.bahr.feature.booking.model.PlaceHoldRequest

/**
 * The booking data the view models read. An interface so view-model tests can hand in a fake
 * (the project uses hand-written fakes, no mocking library).
 */
internal interface BookingRepository {
    suspend fun tripBySlug(slug: String): AppResult<BookingTripDto>

    suspend fun departuresFor(slug: String): AppResult<List<BookingDepartureDto>>

    suspend fun placeHold(request: PlaceHoldRequest): AppResult<HeldSeatsDto>

    suspend fun bookingByRef(
        ref: String,
        phone: String? = null,
    ): AppResult<BookingDto>

    suspend fun heldBooking(
        ref: String,
        phone: String,
    ): AppResult<HeldBookingDto>

    suspend fun releaseHold(
        ref: String,
        phone: String,
    ): AppResult<Unit>
}

/** A pass-through for now: nothing on this screen is worth caching between visits. */
internal class DefaultBookingRepository(
    private val api: BookingApiService,
) : BookingRepository {
    override suspend fun tripBySlug(slug: String): AppResult<BookingTripDto> = api.tripBySlug(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<BookingDepartureDto>> = api.departuresFor(slug)

    override suspend fun placeHold(request: PlaceHoldRequest): AppResult<HeldSeatsDto> = api.placeHold(request)

    override suspend fun bookingByRef(
        ref: String,
        phone: String?,
    ): AppResult<BookingDto> = api.bookingByRef(ref, phone)

    override suspend fun heldBooking(
        ref: String,
        phone: String,
    ): AppResult<HeldBookingDto> = api.heldBooking(ref, phone)

    override suspend fun releaseHold(
        ref: String,
        phone: String,
    ): AppResult<Unit> = api.releaseHold(ref, phone)
}
