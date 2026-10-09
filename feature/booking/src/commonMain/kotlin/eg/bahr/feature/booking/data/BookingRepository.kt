package eg.bahr.feature.booking.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.HeldSeatsDto
import eg.bahr.feature.booking.model.PlaceHoldRequest

internal class BookingRepository(
    private val api: BookingApiService,
) {
    suspend fun placeHold(request: PlaceHoldRequest): AppResult<HeldSeatsDto> = api.placeHold(request)

    suspend fun bookingByRef(
        ref: String,
        phone: String? = null,
    ): AppResult<BookingDto> = api.bookingByRef(ref, phone)

    suspend fun cancel(
        ref: String,
        phone: String? = null,
    ): AppResult<Unit> = api.cancel(ref, phone)
}
