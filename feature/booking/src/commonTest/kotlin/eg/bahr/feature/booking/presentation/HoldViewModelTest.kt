package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The countdown against a fake clock: both the device clock and the server's answers move with the
 * test scheduler's virtual time, so `advanceTimeBy` drives ticks and "now" together.
 *
 * The device clock is set hours off the server's on purpose: nothing here may depend on it agreeing.
 * Never `advanceUntilIdle()` while the ticker runs: it loops until stopped.
 */
class HoldViewModelTest {
    private val hold =
        HoldRoute(
            ref = "BRL-7K4M2X9P",
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
            totalAmount = 900,
            totalCurrency = "EGP",
            guestPhone = "+201012345678",
        )

    /** When the hold was placed, by the server's clock; the hold runs 15 minutes from here. */
    private val placedAt = Instant.parse("2026-10-09T19:27:30Z")

    /** The phone's clock, wrong by hours (and in the past) on purpose. */
    private val deviceStart = Instant.parse("2026-10-09T09:00:00Z")

    private fun TestScope.elapsed(): Duration = testScheduler.currentTime.milliseconds

    private fun TestScope.deviceClock(): Clock =
        object : Clock {
            override fun now(): Instant = deviceStart + elapsed()
        }

    /** The server's answer at the current virtual time: still held until its deadline, then EXPIRED. */
    private fun TestScope.serverAnswer(deadline: Duration = 15.minutes): AppResult<HeldBookingDto> {
        val serverNow = placedAt + elapsed()
        val expires = placedAt + deadline
        return AppResult.Success(
            if (serverNow < expires) {
                BookingFixtures.heldBooking(holdExpiresAt = expires.toString(), serverNow = serverNow.toString())
            } else {
                BookingFixtures.heldBooking(status = "EXPIRED", serverNow = serverNow.toString())
            },
        )
    }

    /** The device's stored hold, as date + party saved it when the hold was placed. */
    private fun storedHold() = FakeActiveHoldStore(StoredHold(hold.ref, hold.guestPhone, hold.holdExpiresAt, hold.serverNow))

    private fun TestScope.started(
        repo: FakeBookingRepository,
        store: FakeActiveHoldStore = storedHold(),
    ): HoldViewModel {
        val vm = HoldViewModel(hold, repo, store, deviceClock())
        vm.onStart()
        runCurrent()
        return vm
    }

    @Test
    fun `counts down from the server's deadline whatever the device clock says`() =
        runViewModelTest {
            val vm = started(FakeBookingRepository(heldBooking = { _, _ -> serverAnswer() }))

            assertEquals(HoldPhase.Holding, vm.uiState.value.phase)
            assertEquals(900, vm.uiState.value.secondsLeft)
            assertEquals(15, vm.uiState.value.minutesLeft)
            assertEquals(1f, vm.uiState.value.progress)

            advanceTimeBy(90.seconds)
            runCurrent()

            assertEquals(810, vm.uiState.value.secondsLeft)
            assertEquals(14, vm.uiState.value.minutesLeft)
            assertEquals(0.9f, vm.uiState.value.progress, 0.001f)
            vm.onStop()
        }

    @Test
    fun `the deadline is re-read from the server when the screen starts`() =
        runViewModelTest {
            // The route is stale (restored after process death): the server says 5 minutes are left.
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer(deadline = 5.minutes) })
            val vm = started(repo)

            assertEquals(300, vm.uiState.value.secondsLeft)
            assertEquals(listOf(hold.ref to hold.guestPhone), repo.bookingReads)
            assertEquals(
                "الفجر على بحيرة البرلس",
                vm.uiState.value.booking
                    ?.trip
                    ?.title,
            )
            vm.onStop()
        }

    @Test
    fun `backgrounded it does not tick and on return it shows the time really left`() =
        runViewModelTest {
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer() })
            val vm = started(repo)
            vm.onStop()

            advanceTimeBy(5.minutes)
            runCurrent()
            // No ticks while hidden: the shown value is stale, and nothing was decremented.
            assertEquals(900, vm.uiState.value.secondsLeft)

            vm.onStart()
            runCurrent()

            assertEquals(600, vm.uiState.value.secondsLeft)
            assertEquals(2, repo.bookingReads.size)
            vm.onStop()
        }

    @Test
    fun `at zero it re-reads the booking and ends when the server says the hold is gone`() =
        runViewModelTest {
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer() })
            val store = storedHold()
            val vm = started(repo, store)
            assertEquals(hold.ref, store.hold.value?.ref)

            advanceTimeBy(15.minutes)
            runCurrent()

            assertEquals(HoldPhase.Expired, vm.uiState.value.phase)
            assertEquals(0, vm.uiState.value.secondsLeft)
            assertEquals(2, repo.bookingReads.size)
            // Over: Home no longer offers it, and the phone leaves the device's storage.
            assertNull(store.hold.value)

            // Ended: no more ticks, no more reads.
            advanceTimeBy(1.minutes)
            runCurrent()
            assertEquals(2, repo.bookingReads.size)
        }

    @Test
    fun `at zero a hold the server still has keeps counting from the fresh deadline`() =
        runViewModelTest {
            // The first answer (on start) gives 15 minutes; by zero, the server's own deadline is
            // 30 seconds later than the device worked out (the device clock ran fast).
            var deadline = 15.minutes
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer(deadline) })
            val vm = started(repo)
            deadline = 15.minutes + 30.seconds

            advanceTimeBy(15.minutes)
            runCurrent()

            assertEquals(HoldPhase.Holding, vm.uiState.value.phase)
            assertEquals(30, vm.uiState.value.secondsLeft)
            vm.onStop()
        }

    @Test
    fun `at zero with no answer it keeps checking with backoff and never assumes the seats are gone`() =
        runViewModelTest {
            var online = true
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ -> if (online) serverAnswer() else AppResult.Failure(AppError.Network) },
                )
            val vm = started(repo)
            online = false

            advanceTimeBy(15.minutes)
            runCurrent()
            assertEquals(HoldPhase.Checking, vm.uiState.value.phase)
            assertEquals(AppError.Network, vm.uiState.value.checkError)
            assertEquals(2, repo.bookingReads.size)

            // Retries after 2 s, then 4 s: not on every tick.
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(2, repo.bookingReads.size)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(3, repo.bookingReads.size)
            advanceTimeBy(3.seconds)
            runCurrent()
            assertEquals(3, repo.bookingReads.size)
            assertEquals(HoldPhase.Checking, vm.uiState.value.phase)

            online = true
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(4, repo.bookingReads.size)
            assertEquals(HoldPhase.Expired, vm.uiState.value.phase)
        }

    @Test
    fun `a hold the server still calls held but with no time left is asked again later not in a loop`() =
        runViewModelTest {
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ ->
                        val serverNow = placedAt + elapsed()
                        // HELD with the deadline exactly now: the sweep has not caught up.
                        val expires = if (elapsed() < 15.minutes) placedAt + 15.minutes else serverNow
                        AppResult.Success(BookingFixtures.heldBooking(holdExpiresAt = expires.toString(), serverNow = serverNow.toString()))
                    },
                )
            val vm = started(repo)

            advanceTimeBy(15.minutes + 1.seconds)
            runCurrent()

            assertEquals(HoldPhase.Checking, vm.uiState.value.phase)
            // The read at zero, then one more after the first 2 s backoff is not yet due.
            assertEquals(2, repo.bookingReads.size)
            vm.onStop()
        }

    @Test
    fun `the spoken minutes change only at minute boundaries`() =
        runViewModelTest {
            val vm = started(FakeBookingRepository(heldBooking = { _, _ -> serverAnswer() }))
            val spoken = mutableListOf(vm.uiState.value.minutesLeft)

            repeat(TICKS_IN_TWO_MINUTES) {
                advanceTimeBy(HoldViewModel.TICK)
                runCurrent()
                val minutes = vm.uiState.value.minutesLeft
                if (minutes != spoken.last()) spoken += minutes
            }

            assertEquals(listOf(15, 14, 13), spoken)
            vm.onStop()
        }

    @Test
    fun `reopened 12 minutes in with the first re-read failing it shows 3 minutes not 15`() =
        runViewModelTest {
            // As Home's card and the second-hold guard hand it over: the fresh pair, plus the full length.
            val reopened =
                hold.copy(
                    serverNow = (placedAt + 12.minutes).toString(),
                    holdExpiresAt = (placedAt + 15.minutes).toString(),
                    holdLengthSeconds = 900,
                )
            val vm =
                HoldViewModel(
                    reopened,
                    FakeBookingRepository(heldBooking = {
                        _,
                        _,
                        ->
                        AppResult.Failure(AppError.Network)
                    }),
                    storedHold(),
                    deviceClock(),
                )
            vm.onStart()
            runCurrent()
            val left = vm.uiState.value.secondsLeft
            val progress = vm.uiState.value.progress
            vm.onStop()

            assertEquals(180, left)
            assertEquals(0.2f, progress, 0.001f)
        }

    @Test
    fun `a hold released elsewhere ends the screen on start`() =
        runViewModelTest {
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ -> AppResult.Success(BookingFixtures.heldBooking(status = "CANCELLED")) },
                )
            val store = storedHold()
            val vm = started(repo, store)

            assertEquals(HoldPhase.Expired, vm.uiState.value.phase)
            assertNull(store.hold.value)
        }

    @Test
    fun `back asks first and confirming releases the hold under the phone it was placed with`() =
        runViewModelTest {
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ -> serverAnswer() },
                    releaseHold = { _, _ -> AppResult.Success(Unit) },
                )
            val store = storedHold()
            val vm = started(repo, store)

            vm.requestLeave()
            assertTrue(vm.uiState.value.isLeaveConfirmVisible)
            assertTrue(repo.releases.isEmpty())

            vm.confirmLeave()
            runCurrent()

            assertEquals(listOf(hold.ref to "+201012345678"), repo.releases)
            assertEquals(HoldPhase.Released, vm.uiState.value.phase)
            assertNull(store.hold.value)
            assertFalse(vm.uiState.value.isLeaveConfirmVisible)
        }

    @Test
    fun `keeping the seats closes the dialog and releases nothing`() =
        runViewModelTest {
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer() })
            val vm = started(repo)

            vm.requestLeave()
            vm.dismissLeave()

            assertFalse(vm.uiState.value.isLeaveConfirmVisible)
            assertEquals(HoldPhase.Holding, vm.uiState.value.phase)
            assertTrue(repo.releases.isEmpty())
            vm.onStop()
        }

    @Test
    fun `a failed release says so and lets the user leave anyway`() =
        runViewModelTest {
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ -> serverAnswer() },
                    releaseHold = { _, _ -> AppResult.Failure(AppError.Network) },
                )
            val store = storedHold()
            val vm = started(repo, store)

            vm.requestLeave()
            vm.confirmLeave()
            runCurrent()

            assertEquals(AppError.Network, vm.uiState.value.releaseError)
            assertEquals(HoldPhase.Holding, vm.uiState.value.phase)
            assertTrue(vm.uiState.value.isLeaveConfirmVisible)

            vm.leaveWithoutRelease()
            assertEquals(HoldPhase.LeftUnreleased, vm.uiState.value.phase)
            // The seats are still held on the server: Home's card stays the way back to them.
            assertEquals(hold.ref, store.hold.value?.ref)
            assertTrue(store.clears.isEmpty())
        }

    @Test
    fun `an unparseable deadline shows zero and checks with the server`() =
        runViewModelTest {
            val repo = FakeBookingRepository(heldBooking = { _, _ -> AppResult.Failure(AppError.Network) })
            val vm = HoldViewModel(hold.copy(holdExpiresAt = "garbled"), repo, FakeActiveHoldStore(), deviceClock())
            assertEquals(0, vm.uiState.value.secondsLeft)

            vm.onStart()
            runCurrent()

            assertEquals(HoldPhase.Checking, vm.uiState.value.phase)
            assertNull(vm.uiState.value.booking)
            assertNotNull(vm.uiState.value.checkError)
            vm.onStop()
        }

    @Test
    fun `the end is reported once`() =
        runViewModelTest {
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ -> AppResult.Success(BookingFixtures.heldBooking(status = "EXPIRED")) },
                )
            val vm = started(repo)
            assertEquals(HoldPhase.Expired, vm.uiState.value.phase)
            assertFalse(vm.uiState.value.isEndReported)

            vm.onEndReported()

            assertTrue(vm.uiState.value.isEndReported)
            assertEquals(HoldPhase.Expired, vm.uiState.value.phase)
        }

    private companion object {
        const val TICKS_IN_TWO_MINUTES = 240
    }
}
