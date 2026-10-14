package eg.bahr.feature.mytrips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.callApi
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * openapi `listMyBookings` and `listMyWaitlist` (M3-B1). Both need the signed-in token, which
 * core:network's session adds to every request.
 */
internal class MyTripsApiService(
    private val client: HttpClient,
) {
    /** [status] is the contract's `upcoming` or `past`. */
    suspend fun bookings(
        status: String,
        page: Int,
        size: Int = PAGE_SIZE,
    ): AppResult<PageDto<MyBookingDto>> =
        callApi {
            client.get("me/bookings") {
                parameter("status", status)
                parameter("page", page)
                parameter("size", size)
            }
        }

    suspend fun waitlist(): AppResult<List<MyWaitlistEntryDto>> = callApi { client.get("me/waitlist") }

    companion object {
        /** The contract's default page size (1..50). */
        const val PAGE_SIZE = 20
    }
}
