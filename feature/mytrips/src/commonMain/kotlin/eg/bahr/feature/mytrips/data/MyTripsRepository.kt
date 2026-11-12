package eg.bahr.feature.mytrips.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eg.bahr.core.catalog.data.AppErrorException
import eg.bahr.core.catalog.data.JoinedWaitlist
import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The two bookings lists of `listMyBookings`. */
internal enum class BookingList(
    val wire: String,
) {
    Upcoming("upcoming"),
    Past("past"),
}

internal interface MyTripsRepository {
    val session: StateFlow<SessionState>

    /** Reads the stored sign-in once (the session starts as Unknown). */
    suspend fun loadSession()

    suspend fun bookings(
        list: BookingList,
        page: Int,
        size: Int = MyTripsApiService.PAGE_SIZE,
    ): AppResult<PageDto<MyBookingDto>>

    suspend fun waitlist(): AppResult<List<MyWaitlistEntryDto>>

    /** Signed out: the waiting-list joins this device made (until sign-in attaches them to the account). */
    val deviceJoins: Flow<List<JoinedWaitlist>>

    /** The trip's title if a list on this device has shown its card; the joins store none. */
    fun cachedTitle(slug: String): String?
}

internal class DefaultMyTripsRepository(
    private val api: MyTripsApiService,
    private val authSession: AuthSession,
    private val waitlists: WaitlistMemory,
    private val cards: TripCardCache,
) : MyTripsRepository {
    override val session: StateFlow<SessionState> = authSession.state

    override suspend fun loadSession() {
        authSession.load()
    }

    override suspend fun bookings(
        list: BookingList,
        page: Int,
        size: Int,
    ) = api.bookings(list.wire, page, size)

    override suspend fun waitlist() = api.waitlist()

    override val deviceJoins: Flow<List<JoinedWaitlist>> = waitlists.joins

    override fun cachedTitle(slug: String): String? = cards.card(slug)?.title
}

/**
 * A bookings list read whole (MV-M2: Upcoming, a handful for anyone), page after page at the contract's largest
 * page size until the server says there is no more. Stops at [maxPages] pages so a wrong total cannot loop; the
 * list then shows what it has (beyond 250 upcoming bookings, which no traveller has). Any page failing fails the
 * whole read: half a list would show a wrong count.
 */
internal suspend fun MyTripsRepository.allBookings(
    list: BookingList,
    maxPages: Int = WHOLE_LIST_MAX_PAGES,
): AppResult<List<MyBookingDto>> {
    val items = mutableListOf<MyBookingDto>()
    var page = 0
    while (page < maxPages) {
        when (val result = bookings(list, page, MyTripsApiService.MAX_PAGE_SIZE)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> {
                items += result.data.items
                if (!result.data.hasMore || result.data.items.isEmpty()) break
            }
        }
        page++
    }
    return AppResult.Success(items)
}

/** 5 pages of 50: the cap on a list read whole. */
internal const val WHOLE_LIST_MAX_PAGES = 5

/**
 * One of the bookings lists (Past, MV-M2), page by page from 0; a refresh starts over at the top. [onTotal] hears
 * the first page's `totalItems`, the section header's count.
 */
internal class MyBookingsPagingSource(
    private val repository: MyTripsRepository,
    private val list: BookingList,
    private val onTotal: (Long) -> Unit = {},
) : PagingSource<Int, MyBookingDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MyBookingDto> {
        val page = params.key ?: FIRST_PAGE
        return when (val result = repository.bookings(list, page)) {
            is AppResult.Success -> {
                if (page == FIRST_PAGE) onTotal(result.data.totalItems)
                LoadResult.Page(
                    data = result.data.items,
                    prevKey = null,
                    // No next page past the total, nor after an empty page (a wrong total cannot loop).
                    nextKey = (page + 1).takeIf { result.data.hasMore && result.data.items.isNotEmpty() },
                )
            }
            is AppResult.Failure -> LoadResult.Error(AppErrorException(result.error))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MyBookingDto>): Int? = null

    private companion object {
        const val FIRST_PAGE = 0
    }
}
