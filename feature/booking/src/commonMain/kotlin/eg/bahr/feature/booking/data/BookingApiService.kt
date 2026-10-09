package eg.bahr.feature.booking.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.booking.model.BookingDepartureDto
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.BookingTripDto
import eg.bahr.feature.booking.model.HeldSeatsDto
import eg.bahr.feature.booking.model.PlaceHoldRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal class BookingApiService(
    private val client: HttpClient,
) {
    /** `getTrip`: the heading, price and party limit of the date + party screen. */
    suspend fun tripBySlug(slug: String): AppResult<BookingTripDto> =
        callApi {
            client.get("trips/$slug")
        }

    /** `listTripDepartures`: the dates with live seat counts (display hints only). */
    suspend fun departuresFor(slug: String): AppResult<List<BookingDepartureDto>> =
        callApi {
            client.get("trips/$slug/departures")
        }

    /**
     * `placeHold`: claims seats. Can fail even against a departure that looked bookable a second ago:
     * the backend claims seats with a single conditional update, so whoever loses that race gets
     * `NO_SEATS_AVAILABLE` rather than an oversold boat.
     */
    suspend fun placeHold(request: PlaceHoldRequest): AppResult<HeldSeatsDto> =
        callApi {
            client.post("bookings") { setBody(request) }
        }

    /**
     * A reference alone is not enough to read someone's booking. A guest who never made an account
     * proves ownership with [phone]; a signed-in customer proves it with the token the client sends.
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
}
