package eg.bahr.feature.mytrips.presentation

import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.mytrips.data.BookingList
import eg.bahr.feature.mytrips.data.FakeMyTripsRepository
import eg.bahr.feature.mytrips.data.MyTripsApiService
import eg.bahr.feature.mytrips.data.MyTripsFixtures
import eg.bahr.feature.mytrips.data.WHOLE_LIST_MAX_PAGES
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyBookingReviewDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import eg.bahr.feature.mytrips.model.ReviewStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * My trips (M3-M0; one list since MV-M2): signed out, the device's joins with what the device knows; signed in,
 * Upcoming and the waiting list read whole on sign-in, Past paged, each failing and retrying on its own; each
 * return to the screen and each pull reads all three again.
 */
class MyTripsViewModelTest {
    private val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))

    @Test
    fun `signed out shows the device's joins - soonest first - with a known title`() =
        runViewModelTest {
            val repository =
                FakeMyTripsRepository(
                    joins = listOf(MyTripsFixtures.join("dep-9", "burg-murals", "2026-11-07"), MyTripsFixtures.join()),
                    titles = mapOf("burullus-dawn" to "Dawn on Lake Burullus"),
                )
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SessionState.SignedOut, state.session)
            assertEquals(listOf("dep-3", "dep-9"), state.deviceJoins.map { it.departureId })
            assertEquals(listOf("Dawn on Lake Burullus", null), state.deviceJoins.map { it.title })
            assertEquals(0, repository.waitlistReads, "no account to read for")
            // Signed out the list is PagingData.empty(), on which asSnapshot() never returns; what proves it
            // is that no bookings are asked for while it is collected.
            val collecting = backgroundScope.launch { vm.pastBookings.collect {} }
            advanceUntilIdle()
            assertTrue(repository.bookingReads.isEmpty())
            collecting.cancel()
        }

    @Test
    fun `signed in - Upcoming and the waiting list are read whole - Past pages from 0 with its total`() =
        runViewModelTest {
            val repository = FakeMyTripsRepository(session = signedIn)
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf("BRL-7K4M2X9P", "BRL-9Q3R5T7V"), (state.upcoming as SectionLoad.Loaded).items.map { it.ref })
            assertEquals(2, (state.waitlist as SectionLoad.Loaded).items.size)
            assertEquals(listOf(BookingList.Upcoming to 0), repository.bookingReads)
            assertEquals(listOf(MyTripsApiService.MAX_PAGE_SIZE), repository.bookingSizes, "Upcoming at the largest page")

            assertEquals(listOf("BRL-2A4B6C8D", "BRL-3E5F7G9H"), vm.pastBookings.asSnapshot().map { it.ref })
            assertEquals(BookingList.Past to 0, repository.bookingReads.last())
            assertEquals(2L, vm.uiState.value.pastTotal, "the first page's totalItems, for the header")
        }

    @Test
    fun `Upcoming spanning pages is read to the end - and stops at the cap`() =
        runViewModelTest {
            fun upcomingPage(page: Int) =
                AppResult.Success(
                    PageDto(
                        items = listOf(MyTripsFixtures.upcoming().first().copy(ref = "BRL-UP$page")),
                        page = page,
                        totalPages = 3,
                    ),
                )
            val repository = FakeMyTripsRepository(session = signedIn, bookings = { _, page -> upcomingPage(page) })
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()
            assertEquals(listOf("BRL-UP0", "BRL-UP1", "BRL-UP2"), (vm.uiState.value.upcoming as SectionLoad.Loaded).items.map { it.ref })

            // A server whose total never ends: the read stops at the cap and shows what it has.
            val endless =
                FakeMyTripsRepository(session = signedIn, bookings = { _, page ->
                    AppResult.Success(
                        PageDto(items = listOf(MyTripsFixtures.upcoming().first().copy(ref = "BRL-E$page")), page = page, totalPages = 999),
                    )
                })
            val vm2 = MyTripsViewModel(endless)
            advanceUntilIdle()
            assertEquals(WHOLE_LIST_MAX_PAGES, (vm2.uiState.value.upcoming as SectionLoad.Loaded).items.size)
        }

    /** The list never waits on Past: Upcoming and the waiting list are shown while Past is still on its way. */
    @Test
    fun `Upcoming and the waiting list show while Past is still loading`() =
        runViewModelTest {
            lateinit var vm: MyTripsViewModel
            var whilePastLoads: MyTripsUiState? = null
            val repository =
                FakeMyTripsRepository(session = signedIn, bookings = { list, _ ->
                    if (list == BookingList.Past) {
                        // A slow Past: what the screen has the moment its first page finally answers.
                        delay(SLOW_PAST_MS)
                        whilePastLoads = vm.uiState.value
                        AppResult.Success(PageDto(MyTripsFixtures.past(), totalPages = 1))
                    } else {
                        AppResult.Success(PageDto(MyTripsFixtures.upcoming(), totalPages = 1))
                    }
                })
            vm = MyTripsViewModel(repository)

            vm.pastBookings.asSnapshot()

            val state = assertNotNull(whilePastLoads, "Past was asked for")
            assertIs<SectionLoad.Loaded<*>>(state.upcoming)
            assertIs<SectionLoad.Loaded<*>>(state.waitlist)
        }

    @Test
    fun `a failed section keeps its error - the others stay - and its retry reads only it`() =
        runViewModelTest {
            var failUpcoming = true
            val repository =
                FakeMyTripsRepository(session = signedIn, bookings = { list, _ ->
                    when {
                        list == BookingList.Upcoming && failUpcoming -> AppResult.Failure(AppError.Network)
                        list == BookingList.Upcoming -> AppResult.Success(PageDto(MyTripsFixtures.upcoming(), totalPages = 1))
                        else -> AppResult.Success(PageDto(MyTripsFixtures.past(), totalPages = 1))
                    }
                })
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()
            assertEquals(SectionLoad.Failed(AppError.Network), vm.uiState.value.upcoming)
            assertIs<SectionLoad.Loaded<*>>(vm.uiState.value.waitlist, "the waiting list stays")
            val waitlistReads = repository.waitlistReads

            failUpcoming = false
            vm.retryUpcoming()
            advanceUntilIdle()
            assertIs<SectionLoad.Loaded<*>>(vm.uiState.value.upcoming)
            assertEquals(waitlistReads, repository.waitlistReads, "the retry read Upcoming only")
        }

    @Test
    fun `a failed waiting list keeps its error until a retry works`() =
        runViewModelTest {
            var fail = true
            val repository =
                FakeMyTripsRepository(
                    session = signedIn,
                    waitlist = { if (fail) AppResult.Failure(AppError.Network) else AppResult.Success(MyTripsFixtures.waitlist()) },
                )
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()
            assertEquals(SectionLoad.Failed(AppError.Network), vm.uiState.value.waitlist)
            assertIs<SectionLoad.Loaded<*>>(vm.uiState.value.upcoming, "Upcoming stays")
            val upcomingReads = repository.bookingReads.count { it.first == BookingList.Upcoming }

            fail = false
            vm.retryWaitlist()
            advanceUntilIdle()
            assertIs<SectionLoad.Loaded<*>>(vm.uiState.value.waitlist)
            assertEquals(upcomingReads, repository.bookingReads.count { it.first == BookingList.Upcoming })
        }

    /** Past paging: the next page is asked for as the list reaches its end, and only while the server has more. */
    @Test
    fun `Past pages as the list scrolls to its end`() =
        runViewModelTest {
            val repository =
                FakeMyTripsRepository(session = signedIn, bookings = { list, page ->
                    if (list == BookingList.Upcoming) {
                        AppResult.Success(PageDto(emptyList(), totalPages = 0))
                    } else {
                        val items =
                            (0 until MyTripsApiService.PAGE_SIZE).map {
                                MyTripsFixtures.past().first().copy(
                                    ref = "BRL-P$page-$it",
                                )
                            }
                        AppResult.Success(PageDto(items, page = page, totalItems = 2L * MyTripsApiService.PAGE_SIZE, totalPages = 2))
                    }
                })
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            val shown = vm.pastBookings.asSnapshot { scrollTo(MyTripsApiService.PAGE_SIZE * 2 - 1) }

            assertEquals(2 * MyTripsApiService.PAGE_SIZE, shown.size)
            assertEquals(listOf(0, 1), repository.bookingReads.filter { it.first == BookingList.Past }.map { it.second })
            assertEquals(2L * MyTripsApiService.PAGE_SIZE, vm.uiState.value.pastTotal)
        }

    @Test
    fun `signing out anywhere turns the screen back to the prompt and empties every section`() =
        runViewModelTest {
            val repository = FakeMyTripsRepository(session = signedIn)
            val vm = MyTripsViewModel(repository)
            backgroundScope.launch { vm.pastBookings.collect {} }
            advanceUntilIdle()
            val readsSignedIn = repository.bookingReads.size

            repository.sessionState.value = SessionState.SignedOut
            advanceUntilIdle()
            vm.refresh()
            vm.pullToRefresh()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SessionState.SignedOut, state.session)
            assertEquals(SectionLoad.Loading, state.upcoming)
            assertEquals(SectionLoad.Loading, state.waitlist)
            assertEquals(null, state.pastTotal)
            assertEquals(readsSignedIn, repository.bookingReads.size, "signed out, no list is read")
        }

    @Test
    fun `the next account never sees the previous account's waiting list or upcoming trips`() =
        runViewModelTest {
            val accountB = CompletableDeferred<AppResult<List<MyWaitlistEntryDto>>>()
            val accountBUpcoming = CompletableDeferred<AppResult<PageDto<MyBookingDto>>>()
            var reads = 0
            var upcomingReads = 0
            val repository =
                FakeMyTripsRepository(
                    session = signedIn,
                    waitlist = { if (++reads == 1) AppResult.Success(MyTripsFixtures.waitlist()) else accountB.await() },
                    bookings = { _, _ ->
                        if (++upcomingReads ==
                            1
                        ) {
                            AppResult.Success(PageDto(MyTripsFixtures.upcoming(), totalPages = 1))
                        } else {
                            accountBUpcoming.await()
                        }
                    },
                )
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()
            assertIs<SectionLoad.Loaded<*>>(vm.uiState.value.waitlist, "account A's list")
            assertIs<SectionLoad.Loaded<*>>(vm.uiState.value.upcoming, "account A's trips")

            repository.sessionState.value = SessionState.SignedOut
            advanceUntilIdle()
            assertEquals(SectionLoad.Loading, vm.uiState.value.waitlist, "gone with A's sign-in")
            assertEquals(SectionLoad.Loading, vm.uiState.value.upcoming, "gone with A's sign-in")

            repository.sessionState.value = SessionState.SignedIn("Omar", profileComplete = true, providers = setOf("apple"))
            advanceUntilIdle()
            assertEquals(SectionLoad.Loading, vm.uiState.value.waitlist, "B's read is under way: nothing of A's shows")
            assertEquals(SectionLoad.Loading, vm.uiState.value.upcoming, "B's read is under way: nothing of A's shows")

            accountB.complete(AppResult.Success(emptyList()))
            accountBUpcoming.complete(AppResult.Success(PageDto(emptyList(), totalPages = 0)))
            advanceUntilIdle()
            assertEquals(SectionLoad.Loaded(emptyList()), vm.uiState.value.waitlist)
            assertEquals(SectionLoad.Loaded(emptyList()), vm.uiState.value.upcoming)
        }

    /** Offline on a return (or a pull): the rows shown stay; the error is only for a first read. */
    @Test
    fun `a failed re-read keeps the rows shown`() =
        runViewModelTest {
            var offline = false
            val repository =
                FakeMyTripsRepository(
                    session = signedIn,
                    bookings = { _, _ ->
                        if (offline) {
                            AppResult.Failure(
                                AppError.Network,
                            )
                        } else {
                            AppResult.Success(PageDto(MyTripsFixtures.upcoming(), totalPages = 1))
                        }
                    },
                    waitlist = { if (offline) AppResult.Failure(AppError.Network) else AppResult.Success(MyTripsFixtures.waitlist()) },
                )
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            offline = true
            vm.refresh()
            advanceUntilIdle()

            assertEquals(2, (vm.uiState.value.upcoming as SectionLoad.Loaded).items.size)
            assertEquals(2, (vm.uiState.value.waitlist as SectionLoad.Loaded).items.size)
        }

    /** M3-M0 review #11: each return to My trips reads all three again (a hold just placed shows), keeping what is shown. */
    @Test
    fun `a visit reads every section again - keeping what is shown`() =
        runViewModelTest {
            val repository = FakeMyTripsRepository(session = signedIn)
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()
            val waitlistReads = repository.waitlistReads

            val shown =
                vm.pastBookings.asSnapshot {
                    vm.refresh()
                    // A re-read never flashes the shown rows back to the skeleton.
                    assertIs<SectionLoad.Loaded<*>>(vm.uiState.value.upcoming)
                }
            advanceUntilIdle()

            assertEquals(2, repository.bookingReads.count { it.first == BookingList.Upcoming })
            assertEquals(2, repository.bookingReads.count { it.first == BookingList.Past })
            assertEquals(waitlistReads + 1, repository.waitlistReads)
            assertEquals(listOf("BRL-2A4B6C8D", "BRL-3E5F7G9H"), shown.map { it.ref })
        }

    @Test
    fun `pull-to-refresh reads all three and shows the indicator until Upcoming and the waiting list are back`() =
        runViewModelTest {
            var gate: CompletableDeferred<Unit>? = null
            val repository =
                FakeMyTripsRepository(session = signedIn, waitlist = {
                    gate?.await()
                    AppResult.Success(MyTripsFixtures.waitlist())
                })
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()
            val upcoming = repository.bookingReads.count { it.first == BookingList.Upcoming }

            // The list is shown (a snapshot collects it) when the pull comes.
            vm.pastBookings.asSnapshot {
                gate = CompletableDeferred()
                vm.pullToRefresh()
            }
            advanceUntilIdle()
            assertEquals(true, vm.uiState.value.refreshing, "the waiting list is still on its way")
            assertIs<SectionLoad.Loaded<*>>(vm.uiState.value.waitlist, "kept while it is read again")

            gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(false, vm.uiState.value.refreshing)
            assertEquals(upcoming + 1, repository.bookingReads.count { it.first == BookingList.Upcoming })
            assertEquals(2, repository.bookingReads.count { it.first == BookingList.Past }, "Past read again too")
        }

    /** M3-M4: back from the review page, Past is read again and the booking shows its pending review, no more "Rate". */
    @Test
    fun `back from reviewing - Past reads again and shows the review's status`() =
        runViewModelTest {
            var reviewed = false
            val repository =
                FakeMyTripsRepository(session = signedIn, bookings = { list, _ ->
                    if (list == BookingList.Upcoming) {
                        AppResult.Success(PageDto(emptyList(), totalPages = 0))
                    } else {
                        val booking = MyTripsFixtures.pastWithReviews().first()
                        val now = if (reviewed) booking.copy(canReview = false, review = MyBookingReviewDto("r9", "pending")) else booking
                        AppResult.Success(PageDto(items = listOf(now), totalPages = 1))
                    }
                })
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            val shown =
                vm.pastBookings.asSnapshot {
                    reviewed = true
                    vm.refresh()
                }

            assertEquals(2, repository.bookingReads.count { it.first == BookingList.Past })
            val booking = shown.single()
            assertEquals(false, booking.canReview)
            assertEquals(ReviewStatus.Pending, booking.review?.status?.let(ReviewStatus::of))
        }
}

/** Past answering long after the other two (virtual time). */
private const val SLOW_PAST_MS = 10_000L
