package eg.bahr.feature.mytrips.presentation

import androidx.paging.testing.asSnapshot
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.mytrips.data.BookingList
import eg.bahr.feature.mytrips.data.FakeMyTripsRepository
import eg.bahr.feature.mytrips.data.MyTripsFixtures
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * My trips (M3-M0): signed out, the device's joins with what the device knows; signed in, the
 * segment's bookings page by page and the waiting list read on sign-in and on each visit to it; since
 * M3-M0b each return to the screen reads the segment again.
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
            val collecting = backgroundScope.launch { vm.bookings.collect {} }
            advanceUntilIdle()
            assertTrue(repository.bookingReads.isEmpty())
            collecting.cancel()
        }

    @Test
    fun `signed in - the selected segment's bookings load page by page`() =
        runViewModelTest {
            val repository = FakeMyTripsRepository(session = signedIn)
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            assertEquals(listOf("BRL-7K4M2X9P", "BRL-9Q3R5T7V"), vm.bookings.asSnapshot().map { it.ref })

            vm.selectSegment(MyTripsSegment.Past)
            assertEquals(listOf("BRL-2A4B6C8D", "BRL-3E5F7G9H"), vm.bookings.asSnapshot().map { it.ref })
            assertEquals(BookingList.Past to 0, repository.bookingReads.last())
        }

    @Test
    fun `the waiting list is read on sign-in and again on each visit to it`() =
        runViewModelTest {
            val repository = FakeMyTripsRepository()
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            repository.sessionState.value = signedIn
            advanceUntilIdle()
            assertEquals(1, repository.waitlistReads)
            assertEquals(2, (vm.uiState.value.waitlist as WaitlistLoad.Loaded).entries.size)

            vm.selectSegment(MyTripsSegment.Waitlist)
            advanceUntilIdle()
            assertEquals(2, repository.waitlistReads)
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
            assertEquals(WaitlistLoad.Failed(AppError.Network), vm.uiState.value.waitlist)

            fail = false
            vm.retryWaitlist()
            advanceUntilIdle()
            assertIs<WaitlistLoad.Loaded>(vm.uiState.value.waitlist)
        }

    @Test
    fun `signing out anywhere turns the screen back to the prompt and empties the list`() =
        runViewModelTest {
            val repository = FakeMyTripsRepository(session = signedIn)
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            backgroundScope.launch { vm.bookings.collect {} }
            advanceUntilIdle()
            val readsSignedIn = repository.bookingReads.size

            repository.sessionState.value = SessionState.SignedOut
            advanceUntilIdle()
            vm.selectSegment(MyTripsSegment.Past)
            advanceUntilIdle()

            assertEquals(SessionState.SignedOut, vm.uiState.value.session)
            assertEquals(readsSignedIn, repository.bookingReads.size, "signed out, no list is read")
        }

    @Test
    fun `the next account never sees the previous account's waiting list`() =
        runViewModelTest {
            val accountB = CompletableDeferred<AppResult<List<MyWaitlistEntryDto>>>()
            var reads = 0
            val repository =
                FakeMyTripsRepository(
                    session = signedIn,
                    waitlist = { if (++reads == 1) AppResult.Success(MyTripsFixtures.waitlist()) else accountB.await() },
                )
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()
            assertIs<WaitlistLoad.Loaded>(vm.uiState.value.waitlist, "account A's list")

            repository.sessionState.value = SessionState.SignedOut
            advanceUntilIdle()
            assertEquals(WaitlistLoad.Loading, vm.uiState.value.waitlist, "gone with A's sign-in")

            repository.sessionState.value = SessionState.SignedIn("Omar", profileComplete = true, providers = setOf("apple"))
            advanceUntilIdle()
            assertEquals(WaitlistLoad.Loading, vm.uiState.value.waitlist, "B's read is under way: nothing of A's shows")

            accountB.complete(AppResult.Success(emptyList()))
            advanceUntilIdle()
            assertEquals(WaitlistLoad.Loaded(emptyList()), vm.uiState.value.waitlist)
        }

    /** M3-M0 review #11: each return to My trips reads Upcoming / Past again (a hold just placed shows). */
    @Test
    fun `a visit reads the segment's bookings again`() =
        runViewModelTest {
            val repository = FakeMyTripsRepository(session = signedIn)
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()

            // The list is shown (a snapshot collects it), then the screen comes back.
            val shown = vm.bookings.asSnapshot { vm.refresh() }

            assertEquals(listOf(BookingList.Upcoming to 0, BookingList.Upcoming to 0), repository.bookingReads)
            assertEquals(listOf("BRL-7K4M2X9P", "BRL-9Q3R5T7V"), shown.map { it.ref })
        }

    @Test
    fun `a visit on the waiting list reads it again - signed out a visit reads nothing`() =
        runViewModelTest {
            val repository = FakeMyTripsRepository(session = signedIn)
            val vm = MyTripsViewModel(repository)
            advanceUntilIdle()
            vm.selectSegment(MyTripsSegment.Waitlist)
            advanceUntilIdle()
            val reads = repository.waitlistReads

            vm.refresh()
            advanceUntilIdle()
            assertEquals(reads + 1, repository.waitlistReads)

            repository.sessionState.value = SessionState.SignedOut
            advanceUntilIdle()
            vm.refresh()
            advanceUntilIdle()
            assertEquals(reads + 1, repository.waitlistReads)
        }
}
