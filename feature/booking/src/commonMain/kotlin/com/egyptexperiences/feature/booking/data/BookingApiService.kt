package com.egyptexperiences.feature.booking.data

import com.egyptexperiences.core.common.result.AppResult
import com.egyptexperiences.core.network.callApi
import com.egyptexperiences.feature.booking.model.BookingDto
import com.egyptexperiences.feature.booking.model.HeldSeatsDto
import com.egyptexperiences.feature.booking.model.PlaceHoldRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class BookingApiService(
    private val client: HttpClient,
) {
    /**
     * Claims seats. Can fail even against a departure that looked bookable a
     * second ago: the backend claims seats with a single conditional update, so
     * whoever loses that race gets an error rather than an oversold boat.
     */
    suspend fun placeHold(request: PlaceHoldRequest): AppResult<HeldSeatsDto> =
        callApi {
            client.post("bookings") { setBody(request) }
        }

    /**
     * A reference alone is not enough to read someone's booking. A guest who
     * never made an account proves ownership with [phone]; a signed-in customer
     * proves it with the token the client already sends.
     */
    suspend fun bookingByRef(
        ref: String,
        phone: String? = null,
    ): AppResult<BookingDto> =
        callApi {
            client.get("bookings/$ref") {
                phone?.let { parameter("phone", it) }
            }
        }

    suspend fun cancel(
        ref: String,
        phone: String? = null,
    ): AppResult<Unit> =
        callApi {
            client.post("bookings/$ref/cancel") {
                phone?.let { parameter("phone", it) }
            }
        }
}
