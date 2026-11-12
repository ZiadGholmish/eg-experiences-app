package eg.bahr.feature.search.data

import eg.bahr.core.catalog.data.HostListRepository
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.model.HostListQuery
import eg.bahr.core.catalog.model.HostPageDto
import eg.bahr.core.common.result.AppResult

/**
 * Hand-written fake of `GET /hosts` (MV-M1), the hosts above search's results. Answers from [listHosts]; by default
 * no host matches, so a test about the trips sees no hosts block. Every request is recorded.
 */
internal class FakeHostListRepository(
    var listHosts: suspend (query: HostListQuery) -> AppResult<HostPageDto> = { AppResult.Success(HostPageDto()) },
) : HostListRepository {
    val requests = mutableListOf<HostRequest>()

    override suspend fun listHosts(
        query: HostListQuery,
        page: Int,
        size: Int,
    ): AppResult<HostPageDto> {
        requests += HostRequest(query, page, size)
        return listHosts.invoke(query)
    }

    companion object {
        fun hosts(vararg cards: HostCardDto) =
            AppResult.Success(HostPageDto(items = cards.toList(), size = cards.size, totalItems = cards.size.toLong(), totalPages = 1))
    }
}

/** One `GET /hosts` call as the fake saw it. */
internal data class HostRequest(
    val query: HostListQuery,
    val page: Int,
    val size: Int,
)
