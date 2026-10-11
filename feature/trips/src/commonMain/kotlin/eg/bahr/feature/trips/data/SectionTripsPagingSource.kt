package eg.bahr.feature.trips.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eg.bahr.core.catalog.data.AppErrorException
import eg.bahr.core.catalog.data.FIRST_PAGE
import eg.bahr.core.catalog.data.tripCardPage
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.model.TripCardPageDto

/**
 * `GET /home/sections/{id}/trips`. [onFirstPage] hears each page-0 answer (for the list's total).
 * A 404 (the section went away since Home was read) is an ordinary error page.
 */
internal class SectionTripsPagingSource(
    private val repository: TripRepository,
    private val sectionId: String,
    private val onFirstPage: (AppResult<TripCardPageDto>) -> Unit = {},
) : PagingSource<Int, TripCardDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TripCardDto> {
        val page = params.key ?: FIRST_PAGE
        val result = repository.sectionTrips(sectionId = sectionId, page = page)
        if (page == FIRST_PAGE) onFirstPage(result)
        return when (result) {
            is AppResult.Success -> tripCardPage(result.data.items, page, result.data.totalPages)
            is AppResult.Failure -> LoadResult.Error(AppErrorException(result.error))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, TripCardDto>): Int? = null
}
