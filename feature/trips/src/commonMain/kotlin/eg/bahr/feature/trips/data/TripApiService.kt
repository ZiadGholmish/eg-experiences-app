package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.TripCardPageDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.TripPageDto
import eg.bahr.feature.trips.model.WaitlistRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * The public catalogue. None of these require a token: a trip link dropped into
 * a social post is opened cold by someone with no account, and that is the
 * funnel release 1 exists to test.
 */
internal class TripApiService(
    private val client: HttpClient,
) {
    /**
     * `listTrips`: one page of the catalogue, narrowed by at most one [category] key and one [filter]
     * value, and by the search text [q] (M4-M3). A null one is left out of the query (the contract's
     * "clear"), never sent empty.
     *
     * [q] goes as the user typed it (Ktor URL-encodes it): the server folds Arabic spelling, case and
     * digits itself, and does so for both languages whatever `Accept-Language` is.
     */
    suspend fun listTrips(
        page: Int = 0,
        size: Int = PAGE_SIZE,
        category: String? = null,
        filter: String? = null,
        q: String? = null,
    ): AppResult<TripPageDto> =
        callApi {
            client.get("trips") {
                // Ktor's `parameter` skips a null value, so a cleared choice is simply absent.
                parameter("category", category)
                parameter("filter", filter)
                parameter("q", q)
                parameter("page", page)
                parameter("size", size)
            }
        }

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

    /**
     * `getHome`: the server-driven sections above the list (PLAN §5c), localised by the client's
     * `Accept-Language`. `GET /trips` still serves the full list under them.
     */
    suspend fun home(): AppResult<HomeDto> =
        callApi {
            client.get("home")
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
         * Matches the backend's `@PageableDefault(size = 20)`, inside both list endpoints' 1..50. Every
         * page of a list is read at this one size, so page numbers line up (see `TripPagingSources`).
         */
        const val PAGE_SIZE = 20

        /**
         * `listTrips`' `q` `maxLength`, in UTF-16 units (`String.length`): a longer one is answered 400
         * VALIDATION_FAILED, so the search field never lets the text grow past it.
         */
        const val MAX_QUERY_LENGTH = 80
    }
}
