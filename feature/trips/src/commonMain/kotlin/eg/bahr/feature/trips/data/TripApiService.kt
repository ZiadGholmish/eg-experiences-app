package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.callApi
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.TripPageDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

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

    companion object {
        /** Matches the backend's `@PageableDefault(size = 20)`. */
        const val PAGE_SIZE = 20
    }
}
