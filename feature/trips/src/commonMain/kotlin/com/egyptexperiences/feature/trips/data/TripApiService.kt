package com.egyptexperiences.feature.trips.data

import com.egyptexperiences.core.common.result.AppResult
import com.egyptexperiences.core.network.PageDto
import com.egyptexperiences.core.network.callApi
import com.egyptexperiences.feature.trips.model.DepartureDto
import com.egyptexperiences.feature.trips.model.TripDetailDto
import com.egyptexperiences.feature.trips.model.TripSummaryDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * The public catalogue. None of these require a token: a trip link dropped into
 * a social post is opened cold by someone with no account, and that is the
 * funnel release 1 exists to test.
 */
class TripApiService(
    private val client: HttpClient,
) {
    suspend fun listTrips(
        page: Int = 0,
        size: Int = PAGE_SIZE,
    ): AppResult<PageDto<TripSummaryDto>> =
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
