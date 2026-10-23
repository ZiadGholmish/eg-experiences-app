package eg.bahr.feature.host.data

import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.host.model.HostProfileDto

/**
 * The host page's data. Their trips are `listTrips` with `host`, from core:catalog's [TripListRepository]. An
 * interface so view-model tests can hand in a fake (the project uses hand-written fakes, no mocking library).
 */
internal interface HostRepository : TripListRepository {
    /** A host's public page (M4-M10). Their trips are [listTrips] with `host`. */
    suspend fun hostBySlug(slug: String): AppResult<HostProfileDto>
}

/** A pass-through to [api], with `GET /trips` (which remembers every card it loads) from [lists]. */
internal class DefaultHostRepository(
    private val api: HostApiService,
    private val lists: TripListRepository,
) : HostRepository,
    TripListRepository by lists {
    override suspend fun hostBySlug(slug: String): AppResult<HostProfileDto> = api.hostBySlug(slug)
}
