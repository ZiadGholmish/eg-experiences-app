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
    ) = api.bookings(list.wire, page)

    override suspend fun waitlist() = api.waitlist()

    override val deviceJoins: Flow<List<JoinedWaitlist>> = waitlists.joins

    override fun cachedTitle(slug: String): String? = cards.card(slug)?.title
}

/** One of the bookings lists, page by page from 0; a refresh starts over at the top. */
internal class MyBookingsPagingSource(
    private val repository: MyTripsRepository,
    private val list: BookingList,
) : PagingSource<Int, MyBookingDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MyBookingDto> {
        val page = params.key ?: FIRST_PAGE
        return when (val result = repository.bookings(list, page)) {
            is AppResult.Success ->
                LoadResult.Page(
                    data = result.data.items,
                    prevKey = null,
                    // No next page past the total, nor after an empty page (a wrong total cannot loop).
                    nextKey = (page + 1).takeIf { result.data.hasMore && result.data.items.isNotEmpty() },
                )
            is AppResult.Failure -> LoadResult.Error(AppErrorException(result.error))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MyBookingDto>): Int? = null

    private companion object {
        const val FIRST_PAGE = 0
    }
}
