package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.HostListQuery
import eg.bahr.core.catalog.model.HostPageDto
import eg.bahr.core.common.result.AppResult

/**
 * `GET /hosts` (MV-B1): the hosts directory (feature:host) and the hosts above search's results (feature:search).
 * An interface so each feature's view-model tests hand in a fake.
 */
interface HostListRepository {
    /** One page of the directory, narrowed by [query]. */
    suspend fun listHosts(
        query: HostListQuery,
        page: Int = FIRST_PAGE,
        size: Int = HOST_PAGE_SIZE,
    ): AppResult<HostPageDto>
}

/** A pass-through to [api]. */
internal class DefaultHostListRepository(
    private val api: HostListApiService,
) : HostListRepository {
    override suspend fun listHosts(
        query: HostListQuery,
        page: Int,
        size: Int,
    ): AppResult<HostPageDto> = api.listHosts(query, page, size)
}
