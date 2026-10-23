package eg.bahr.feature.triplisting.data

import eg.bahr.core.catalog.data.TRIP_PAGE_SIZE
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.triplisting.model.TripCardPageDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * A Home row's "See all". `GET /trips` (the category page) is core:catalog's `TripListApiService`, shared with Home
 * (M4-M1c). Needs no token, as every trip read.
 */
internal class TripListingApiService(
    private val client: HttpClient,
) {
    /**
     * `listHomeSectionTrips`: a page of the whole list one Home `trips` row is drawn from (its "See
     * all"), in the row's order. Carries no title: the row's travels with the navigation.
     */
    suspend fun sectionTrips(
        sectionId: String,
        page: Int = 0,
        size: Int = PAGE_SIZE,
    ): AppResult<TripCardPageDto> =
        callApi {
            client.get("home/sections/$sectionId/trips") {
                parameter("page", page)
                parameter("size", size)
            }
        }

    companion object {
        /**
         * The one page size every trip list reads at (core:catalog's, where `GET /trips` lives since
         * M4-M1c), so a "See all" page lines up with the paging setup they share.
         */
        const val PAGE_SIZE = TRIP_PAGE_SIZE
    }
}
