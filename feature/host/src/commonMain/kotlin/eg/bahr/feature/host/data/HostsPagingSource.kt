package eg.bahr.feature.host.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import eg.bahr.core.catalog.data.AppErrorException
import eg.bahr.core.catalog.data.FIRST_PAGE
import eg.bahr.core.catalog.data.HOST_PAGE_SIZE
import eg.bahr.core.catalog.data.HostListRepository
import eg.bahr.core.catalog.model.HostCardDto
import eg.bahr.core.catalog.model.HostListQuery
import eg.bahr.core.catalog.model.HostPageDto
import eg.bahr.core.common.result.AppResult

/**
 * The hosts directory's paging (MV-M1), set up as the trip lists' (`TripPagingConfig`): every page at the one size
 * the source sends, so Paging's default first load of three pages (60, past `listHosts`' maximum of 50) never goes
 * out, and the next page is asked for within half a page of the end. No placeholders: a card has no blank form.
 */
internal val HostPagingConfig =
    PagingConfig(
        pageSize = HOST_PAGE_SIZE,
        prefetchDistance = HOST_PAGE_SIZE / 2,
        initialLoadSize = HOST_PAGE_SIZE,
        enablePlaceholders = false,
    )

/**
 * `GET /hosts` narrowed by [query], a page at a time from page 0. [onFirstPage] hears each page-0 answer, success
 * or failure, so the view model reads the region chips and the total (or falls back from a stale key) without a
 * second request. Keys are the server's page numbers; `params.loadSize` is ignored (see [HostPagingConfig]).
 */
internal class HostsPagingSource(
    private val repository: HostListRepository,
    private val query: HostListQuery,
    private val onFirstPage: (AppResult<HostPageDto>) -> Unit = {},
) : PagingSource<Int, HostCardDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, HostCardDto> {
        val page = params.key ?: FIRST_PAGE
        val result = repository.listHosts(query, page = page, size = HOST_PAGE_SIZE)
        if (page == FIRST_PAGE) onFirstPage(result)
        return when (result) {
            is AppResult.Success ->
                LoadResult.Page(
                    data = result.data.items,
                    prevKey = null,
                    // None past the last page, and none after an empty one, so a wrong total cannot keep it asking.
                    nextKey = if (result.data.items.isNotEmpty() && page + 1 < result.data.totalPages) page + 1 else null,
                )
            is AppResult.Failure -> LoadResult.Error(AppErrorException(result.error))
        }
    }

    // A refresh starts over at the top: the list is always read from page 0.
    override fun getRefreshKey(state: PagingState<Int, HostCardDto>): Int? = null
}
