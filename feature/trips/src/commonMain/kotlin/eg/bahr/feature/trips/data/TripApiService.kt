package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.trips.model.DepartureDto
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
    suspend fun listTrips(
        page: Int = 0,
        size: Int = PAGE_SIZE,
    ): AppResult<TripPageDto> =
        callApi {
            client.get("trips") {
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
        /** Matches the backend's `@PageableDefault(size = 20)`. */
        const val PAGE_SIZE = 20
    }
}
