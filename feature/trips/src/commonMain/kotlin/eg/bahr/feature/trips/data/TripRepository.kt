package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.PageDto
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.TripSummaryDto

/**
 * The trips data the view models read. An interface so view-model tests can hand in a fake
 * (the project uses hand-written fakes, no mocking library).
 */
interface TripRepository {
    suspend fun listTrips(page: Int = 0): AppResult<PageDto<TripSummaryDto>>

    suspend fun tripBySlug(slug: String): AppResult<TripDetailDto>

    suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>>
}

/**
 * A pass-through today. It exists as the seam where caching goes: the
 * requirements doc calls for an offline-renderable ticket, and trip detail is
 * the other read worth holding onto between launches.
 */
class DefaultTripRepository(
    private val api: TripApiService,
) : TripRepository {
    override suspend fun listTrips(page: Int): AppResult<PageDto<TripSummaryDto>> = api.listTrips(page = page)

    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = api.tripBySlug(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> = api.departuresFor(slug)
}
