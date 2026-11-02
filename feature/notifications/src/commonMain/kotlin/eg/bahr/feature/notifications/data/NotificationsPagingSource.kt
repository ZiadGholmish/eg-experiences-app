package eg.bahr.feature.notifications.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.notifications.model.NotificationDto

/** An item as one read of the inbox loaded it: [generation] tells which read, so edits made before a re-read stop at it. */
internal data class LoadedNotification(
    val dto: NotificationDto,
    val generation: Int,
)

/** A failed page, carrying the error the screen branches on. */
internal class InboxLoadException(
    val error: AppError,
) : Exception(error.toString())

/**
 * One read of [owner]'s inbox, a page at a time from 0; a refresh is a new source, numbered [generation].
 *
 * New items arrive at the top while the person scrolls, so a later page can repeat the last items of an earlier one
 * (openapi `listMyNotifications`: "de-duplicate by `id`"): each id is kept once per read, as a list key must be.
 * [onFirstPage] hears each page-0 answer that landed.
 */
internal class NotificationsPagingSource(
    private val repository: NotificationsRepository,
    private val owner: InboxOwner,
    val generation: Int,
    private val onFirstPage: () -> Unit = {},
) : PagingSource<Int, LoadedNotification>() {
    private val seen = mutableSetOf<String>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LoadedNotification> {
        val page = params.key ?: FIRST_PAGE
        return when (val result = repository.page(owner, page)) {
            is AppResult.Success -> {
                if (page == FIRST_PAGE) onFirstPage()
                val items = result.data.items
                LoadResult.Page(
                    data = items.filter { seen.add(it.id) }.map { LoadedNotification(it, generation) },
                    prevKey = null,
                    // No next page past the total, nor after an empty page (a wrong total cannot loop).
                    nextKey = (page + 1).takeIf { result.data.hasMore && items.isNotEmpty() },
                )
            }
            is AppResult.Failure -> LoadResult.Error(InboxLoadException(result.error))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, LoadedNotification>): Int? = null

    private companion object {
        const val FIRST_PAGE = 0
    }
}
