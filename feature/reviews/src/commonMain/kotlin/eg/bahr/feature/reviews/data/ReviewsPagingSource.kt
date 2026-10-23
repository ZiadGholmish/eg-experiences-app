package eg.bahr.feature.reviews.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eg.bahr.core.catalog.data.AppErrorException
import eg.bahr.core.catalog.data.FIRST_PAGE
import eg.bahr.core.catalog.model.ReviewDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.reviews.model.ReviewPageDto
import eg.bahr.feature.reviews.model.ReviewSort

/**
 * What one reviews list asks `listReviews` for: a trip's reviews ([trip]) or a host's ([host]), exactly
 * one of them, in [sort] order, narrowed to [stars] stars when set.
 */
internal data class ReviewQuery(
    val trip: String?,
    val host: String?,
    val sort: ReviewSort = ReviewSort.NEWEST,
    val stars: Int? = null,
)

/**
 * `GET /reviews` (M4-M11), a page at a time, at the trip lists' page size. [onFirstPage] hears each
 * page-0 answer, success or failure, so the view model reads the rating summary (the header and the star
 * chips' counts) and the total, or a NOT_FOUND, without a second request.
 *
 * Like the trip lists' pages (core:catalog's `tripCardPage`): nothing is prepended (a refresh starts at
 * page 0), and there is no next page past `totalPages` or after an empty page, so a wrong total cannot
 * keep the list asking.
 */
internal class ReviewsPagingSource(
    private val repository: ReviewsRepository,
    private val query: ReviewQuery,
    private val onFirstPage: (AppResult<ReviewPageDto>) -> Unit = {},
) : PagingSource<Int, ReviewDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ReviewDto> {
        val page = params.key ?: FIRST_PAGE
        val result = repository.reviews(trip = query.trip, host = query.host, sort = query.sort, stars = query.stars, page = page)
        if (page == FIRST_PAGE) onFirstPage(result)
        return when (result) {
            is AppResult.Success -> {
                val items = result.data.items
                LoadResult.Page(
                    data = items,
                    prevKey = null,
                    nextKey = if (items.isNotEmpty() && page + 1 < result.data.totalPages) page + 1 else null,
                )
            }

            is AppResult.Failure -> LoadResult.Error(AppErrorException(result.error))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ReviewDto>): Int? = null
}
