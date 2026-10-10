package eg.bahr.feature.map.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.map.model.TripMapDto

/** What the map's view model reads. An interface so its tests hand in a fake (no mocking library). */
internal interface TripMapRepository {
    suspend fun tripMap(): AppResult<TripMapDto>
}

/** A pass-through today: the map is read fresh each time it opens (seat counts are hints that go stale). */
internal class DefaultTripMapRepository(
    private val api: TripMapApiService,
) : TripMapRepository {
    override suspend fun tripMap(): AppResult<TripMapDto> = api.tripMap()
}
