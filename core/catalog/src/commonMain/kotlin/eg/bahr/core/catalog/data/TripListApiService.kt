package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.TripPageDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * `GET /trips`, the public catalogue list, read by Home's "All trips" (feature:home), the category
 * page and search (feature:trips). One owner here, so the two features do not each send it (M4-M1c).
 * No token: a trip link dropped into a social post is opened cold by someone with no account.
 */
internal class TripListApiService(
    private val client: HttpClient,
) {
    /**
     * `listTrips`: one page of the catalogue, narrowed by at most one [category] key and one [filter]
     * value, and by the search text [q] (M4-M3). A null one is left out of the query (the contract's
     * "clear"), never sent empty.
     *
     * [q] goes as the user typed it (Ktor URL-encodes it): the server folds Arabic spelling, case and
     * digits itself, and does so for both languages whatever `Accept-Language` is.
     *
     * [host] (M4-B7) is a host's public slug: only that host's trips, the host page's list. A slug with
     * no public profile is answered NOT_FOUND, never an empty page.
     */
    suspend fun listTrips(
        page: Int = 0,
        size: Int = PAGE_SIZE,
        category: String? = null,
        filter: String? = null,
        q: String? = null,
        host: String? = null,
    ): AppResult<TripPageDto> =
        callApi {
            client.get("trips") {
                // Ktor's `parameter` skips a null value, so a cleared choice is simply absent.
                parameter("category", category)
                parameter("filter", filter)
                parameter("q", q)
                parameter("host", host)
                parameter("page", page)
                parameter("size", size)
            }
        }

    companion object {
        const val PAGE_SIZE = TRIP_PAGE_SIZE
    }
}

/**
 * Matches the backend's `@PageableDefault(size = 20)`, inside both list endpoints' 1..50. Every page of
 * every trip list (here, and feature:trips' "See all") is read at this one size, so page numbers line
 * up (see `TripPagingSources`).
 */
const val TRIP_PAGE_SIZE = 20
