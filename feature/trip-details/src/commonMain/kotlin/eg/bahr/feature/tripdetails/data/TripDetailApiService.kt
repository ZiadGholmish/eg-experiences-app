package eg.bahr.feature.tripdetails.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.withDeviceId
import eg.bahr.core.network.callApi
import eg.bahr.feature.tripdetails.model.DepartureDto
import eg.bahr.feature.tripdetails.model.TripDetailDto
import eg.bahr.feature.tripdetails.model.WaitlistRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * The trip page and its waiting list. A Home row's "See all" is feature:trip-listing's, the host page feature:host's and
 * the reviews list feature:reviews' (split from feature:trips, M3-M16); `GET /trips` is core:catalog's `TripListApiService`.
 *
 * None of these require a token: a trip link dropped into a social post is opened cold by someone
 * with no account, and that is the funnel release 1 exists to test.
 */
internal class TripDetailApiService(
    private val client: HttpClient,
) {
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
     * `joinWaitlist`: puts an email on a sold-out date's waiting list. Lives with the trip page, not
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
            client.post("departures/$departureId/waitlist") {
                // So a signed-in account can claim this device's anonymous entry (M3-B1).
                withDeviceId()
                setBody(request)
            }
        }
}
