package com.egyptexperiences.feature.booking.data

import com.egyptexperiences.core.common.result.AppResult
import com.egyptexperiences.feature.booking.model.BookingDto
import com.egyptexperiences.feature.booking.model.HeldSeatsDto
import com.egyptexperiences.feature.booking.model.PlaceHoldRequest

class BookingRepository(
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
