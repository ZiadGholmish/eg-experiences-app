package com.egyptexperiences.feature.trips.data

import com.egyptexperiences.core.common.result.AppResult
import com.egyptexperiences.core.network.PageDto
import com.egyptexperiences.feature.trips.model.DepartureDto
import com.egyptexperiences.feature.trips.model.TripDetailDto
import com.egyptexperiences.feature.trips.model.TripSummaryDto

/**
 * A pass-through today. It exists as the seam where caching goes: the
 * requirements doc calls for an offline-renderable ticket, and trip detail is
 * the other read worth holding onto between launches.
 */
class TripRepository(
    private val api: TripApiService,
) {
    suspend fun listTrips(page: Int = 0): AppResult<PageDto<TripSummaryDto>> = api.listTrips(page = page)

    suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = api.tripBySlug(slug)

    suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> = api.departuresFor(slug)
}
