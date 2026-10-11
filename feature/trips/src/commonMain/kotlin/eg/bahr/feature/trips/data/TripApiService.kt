package eg.bahr.feature.trips.data

import eg.bahr.core.catalog.data.TRIP_PAGE_SIZE
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripCardPageDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.WaitlistRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * The trip page, a Home row's "See all" and the waiting list. `GET /trips` (the category page and
 * search) is core:catalog's `TripListApiService`, shared with Home (M4-M1c).
 *
 * None of these require a token: a trip link dropped into a social post is opened cold by someone
 * with no account, and that is the funnel release 1 exists to test.
 */
internal class TripApiService(
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

    /** Slugs are what appear in shared links, so this is the busiest read. */
    suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> =
        callApi {
            client.get("trips/$slug")
        }

    suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> =
        callApi {
            client.get("trips/$slug/departures")
        }

    /**
     * `joinWaitlist`: puts a phone on a sold-out date's waiting list. Lives with the trip page, not
     * in `feature:booking`, because the button sits on the trip page's sold-out notice and the call
     * is a departure-level public endpoint; owning it here avoids a feature-to-feature edge.
     *
     * The 201 body is `{"success":true}` and says nothing more (a repeat join answers the same), so
     * it reads as [Unit].
     */
    suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit> =
        callApi {
            client.post("departures/$departureId/waitlist") { setBody(request) }
        }

    companion object {
        /**
         * The one page size every trip list reads at (core:catalog's, where `GET /trips` lives since
         * M4-M1c), so a "See all" page lines up with the paging setup they share.
         */
        const val PAGE_SIZE = TRIP_PAGE_SIZE

        /**
         * `listTrips`' `q` `maxLength`, in UTF-16 units (`String.length`): a longer one is answered 400
         * VALIDATION_FAILED, so the search field never lets the text grow past it.
         */
        const val MAX_QUERY_LENGTH = 80
    }
}
