package eg.bahr.core.catalog.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.model.TripPageDto
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult

/*
 * One PagingSource per list endpoint (M4-M1b): `GET /trips` here (Home's "All trips", the category page
 * and search), and feature:trips' `SectionTripsPagingSource` for `GET /home/sections/{id}/trips` (a
 * row's "See all"). Keys are the server's page numbers, from 0.
 *
 * Every page is read at the one size the ApiService sends (`TripListApiService.PAGE_SIZE`), so the source
 * ignores `params.loadSize`: Paging's default first load is three pages' worth (60), which is past
 * the endpoints' maximum of 50, and pages of mixed sizes would not line up by number.
 *
 * Paging does not de-duplicate. A trip published between two page reads shifts the next page by one,
 * so a card can repeat; the view models drop repeats per generation (see `distinctTrips`).
 */

/**
 * The one paging setup for every trip list: fixed pages, no placeholders (cards have no blank form).
 * The next page is asked for when the user is within half a page of the end: Paging's default (a
 * whole page) would ask for page 1 straight after page 0 on every open, since the first screen of
 * cards is already within 20 of the end.
 */
val TripPagingConfig =
    PagingConfig(
        pageSize = TripListApiService.PAGE_SIZE,
        prefetchDistance = TripListApiService.PAGE_SIZE / 2,
        initialLoadSize = TripListApiService.PAGE_SIZE,
        enablePlaceholders = false,
    )

/**
 * A failed page as the Throwable Paging carries in `LoadState.Error`. The screen reads [error] back
 * (via [appError]) to pick the localised message and whether a retry makes sense.
 */
class AppErrorException(
    val error: AppError,
) : Exception(error.toString())

/** The [AppError] behind a `LoadState.Error`; anything else is [AppError.Unknown]. */
fun Throwable.appError(): AppError = (this as? AppErrorException)?.error ?: AppError.Unknown(message)

/**
 * `GET /trips`, narrowed by [category], [filter] and the search text [q] (null = not sent). With [q]
 * the server orders the pages best match first; they are shown in that order. [onFirstPage] hears every
 * page-0 answer, success or failure, so the view model can read the facets and the total, or fall
 * back from a stale key, without a second request.
 */
class TripListPagingSource(
    private val repository: TripListRepository,
    private val category: String? = null,
    private val filter: String? = null,
    private val q: String? = null,
    private val onFirstPage: (AppResult<TripPageDto>) -> Unit = {},
) : PagingSource<Int, TripCardDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TripCardDto> {
        val page = params.key ?: FIRST_PAGE
        val result = repository.listTrips(page = page, category = category, filter = filter, q = q)
        if (page == FIRST_PAGE) onFirstPage(result)
        return when (result) {
            is AppResult.Success -> tripCardPage(result.data.items, page, result.data.totalPages)
            is AppResult.Failure -> LoadResult.Error(AppErrorException(result.error))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, TripCardDto>): Int? = null
}

/**
 * A loaded page, for every trip list's PagingSource (this one and feature:trips' "See all"). Lists
 * always start at page 0 (a refresh re-reads from the top, `getRefreshKey` is null), so nothing is
 * ever prepended. There is no next page past [totalPages], and none after an
 * empty page either, so a wrong total cannot keep the list asking.
 */
fun tripCardPage(
    items: List<TripCardDto>,
    page: Int,
    totalPages: Int,
): PagingSource.LoadResult.Page<Int, TripCardDto> =
    PagingSource.LoadResult.Page(
        data = items,
        prevKey = null,
        nextKey = if (items.isNotEmpty() && page + 1 < totalPages) page + 1 else null,
    )

/** The page every trip list starts at (the server counts from 0). */
const val FIRST_PAGE = 0
