package eg.bahr.feature.mytrips.data

import eg.bahr.core.catalog.data.JoinedWaitlist
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyDepartureDto
import eg.bahr.feature.mytrips.model.MyTripDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

internal class FakeMyTripsRepository(
    session: SessionState = SessionState.SignedOut,
    var bookings: suspend (BookingList, Int) -> AppResult<PageDto<MyBookingDto>> = { list, _ ->
        AppResult.Success(
            PageDto(
                items =
                    if (list ==
                        BookingList.Upcoming
                    ) {
                        MyTripsFixtures.upcoming()
                    } else {
                        MyTripsFixtures.past()
                    },
                totalPages = 1,
            ),
        )
    },
    var waitlist: suspend () -> AppResult<List<MyWaitlistEntryDto>> = { AppResult.Success(MyTripsFixtures.waitlist()) },
    joins: List<JoinedWaitlist> = emptyList(),
    private val titles: Map<String, String> = emptyMap(),
) : MyTripsRepository {
    val sessionState = MutableStateFlow(session)
    override val session: StateFlow<SessionState> = sessionState
    val joinsState = MutableStateFlow(joins)
    override val deviceJoins = joinsState

    var waitlistReads = 0
    val bookingReads = mutableListOf<Pair<BookingList, Int>>()

    override suspend fun loadSession() = Unit

    override suspend fun bookings(
        list: BookingList,
        page: Int,
    ): AppResult<PageDto<MyBookingDto>> {
        bookingReads += list to page
        return bookings.invoke(list, page)
    }

    override suspend fun waitlist(): AppResult<List<MyWaitlistEntryDto>> {
        waitlistReads++
        return waitlist.invoke()
    }

    override fun cachedTitle(slug: String): String? = titles[slug]
}

/** Values as openapi's `Booking` and `MyWaitlistEntry` examples carry them. */
internal object MyTripsFixtures {
    private val dawn = MyTripDto(slug = "burullus-dawn", title = "الفجر على بحيرة البرلس")
    private val murals = MyTripDto(slug = "burg-murals", title = "جداريات برج البرلس")

    fun upcoming() =
        listOf(
            MyBookingDto(
                ref = "BRL-7K4M2X9P",
                status = "CONFIRMED",
                date = "2026-10-24",
                partySize = 2,
                total = MoneyDto(900, "EGP"),
                trip = dawn,
                departure = MyDepartureDto(timeLocal = "05:00"),
            ),
            MyBookingDto(
                ref = "BRL-9Q3R5T7V",
                status = "HELD",
                date = "2026-10-31",
                partySize = 1,
                holdExpiresAt = "2026-10-14T12:15:00+03:00",
                trip = murals,
                departure = MyDepartureDto(timeLocal = "07:30"),
            ),
        )

    fun past() =
        listOf(
            MyBookingDto(ref = "BRL-2A4B6C8D", status = "COMPLETED", date = "2026-09-12", partySize = 3, trip = dawn),
            MyBookingDto(ref = "BRL-3E5F7G9H", status = "CANCELLED", date = "2026-08-29", partySize = 2, trip = murals),
        )

    fun waitlist() =
        listOf(
            MyWaitlistEntryDto(
                id = "e1",
                departureId = "dep-3",
                date = "2026-10-24",
                partySize = 2,
                departTime = "05:00",
                seatsRemaining = 2,
                soldOut = false,
                bookable = true,
                trip = dawn,
            ),
            MyWaitlistEntryDto(
                id = "e2",
                departureId = "dep-9",
                date = "2026-11-07",
                partySize = 4,
                departTime = "07:30",
                unavailableReason = "SOLD_OUT",
                trip = murals,
            ),
        )

    fun join(
        departureId: String = "dep-3",
        slug: String = "burullus-dawn",
        date: String = "2026-10-24",
    ) = JoinedWaitlist(departureId = departureId, tripSlug = slug, date = LocalDate.parse(date), partySize = 2, email = "sara@example.com")
}
