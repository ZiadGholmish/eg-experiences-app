package eg.bahr.feature.booking.presentation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.model.HeldBookingDto
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Home's "Continue your booking" card against a fake clock: the device clock and the server's
 * answers both move with the test scheduler's virtual time, and the device clock is hours off the
 * server's on purpose. Never `advanceUntilIdle()` while the card ticks: it loops until stopped.
 */
class ContinueBookingViewModelTest {
    /** When the hold was placed, by the server's clock; it runs 15 minutes from here. */
    private val placedAt = Instant.parse("2026-10-09T19:27:30Z")

    /** The phone's clock, wrong by hours (and in the past) on purpose. */
    private val deviceStart = Instant.parse("2026-10-09T09:00:00Z")

    private val stored =
        StoredHold(
            ref = "BRL-7K4M2X9P",
            guestPhone = "+201012345678",
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
        )

    private fun TestScope.elapsed(): Duration = testScheduler.currentTime.milliseconds

    private fun TestScope.deviceClock(): Clock =
        object : Clock {
            override fun now(): Instant = deviceStart + elapsed()
        }

    /**
     * The server's answer at the current virtual time, [sinceHold] after the hold was placed: held
     * until its deadline, then EXPIRED (with no deadline, as the contract has it).
     */
    private fun TestScope.serverAnswer(sinceHold: Duration = Duration.ZERO): AppResult<HeldBookingDto> {
        val serverNow = placedAt + sinceHold + elapsed()
        val expires = placedAt + 15.minutes
        return AppResult.Success(
            if (serverNow < expires) {
                BookingFixtures.heldBooking(holdExpiresAt = expires.toString(), serverNow = serverNow.toString())
            } else {
                BookingFixtures.heldBooking(status = "EXPIRED", serverNow = serverNow.toString())
            },
        )
    }

    private fun TestScope.started(
        repo: FakeBookingRepository,
        store: FakeActiveHoldStore = FakeActiveHoldStore(stored),
    ): ContinueBookingViewModel {
        val vm = ContinueBookingViewModel(store, repo, deviceClock())
        vm.onStart()
        runCurrent()
        return vm
    }

    @Test
    fun `no stored hold shows nothing and reads nothing`() =
        runViewModelTest {
            val repo = FakeBookingRepository()
            val vm = started(repo, FakeActiveHoldStore())
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isVisible)
            assertTrue(repo.bookingReads.isEmpty())
        }

    @Test
    fun `a live stored hold shows with the server's countdown whatever the device clock says`() =
        runViewModelTest {
            // Home opens five minutes after the hold was placed (an app restart in between).
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer(sinceHold = 5.minutes) })
            val vm = started(repo)

            val state = vm.uiState.value
            assertTrue(state.isVisible)
            assertEquals(listOf(stored.ref to stored.guestPhone), repo.bookingReads)
            assertEquals(600, state.secondsLeft)
            // Two thirds of the hold's full 15 minutes (from the placement) left.
            assertEquals(2f / 3f, state.progress, 0.001f)
            assertEquals(BookingFixtures.heldBooking().trip?.title, state.booking?.trip?.title)

            advanceTimeBy(90.seconds)
            runCurrent()
            assertEquals(510, vm.uiState.value.secondsLeft)
            vm.onStop()
        }

    @Test
    fun `tapping hands over the stored hold with the server's total`() =
        runViewModelTest {
            val vm = started(FakeBookingRepository(heldBooking = { _, _ -> serverAnswer(sinceHold = 5.minutes) }))

            val route = vm.routeForTap()
            assertEquals(stored.ref, route?.ref)
            assertEquals(stored.guestPhone, route?.guestPhone)
            // The fresh answer's pair for the countdown; the placement's for the bar's 100 %.
            assertEquals((placedAt + 15.minutes).toString(), route?.holdExpiresAt)
            assertEquals((placedAt + 5.minutes).toString(), route?.serverNow)
            assertEquals(900L, route?.holdLengthSeconds)

            // A tap a minute later carries the server's clock a minute on, not the card's old read.
            advanceTimeBy(60.seconds)
            runCurrent()
            assertEquals(
                (placedAt + 6.minutes).toString(),
                vm
                    .routeForTap()
                    ?.serverNow,
            )
            assertEquals(900L, route?.totalAmount)
            assertEquals("EGP", route?.totalCurrency)
            vm.onStop()
        }

    @Test
    fun `the hold screen opened from the card counts from the server's time left even offline`() =
        runViewModelTest {
            // Home opens 12 minutes into the hold: 3 minutes left by the server's clock.
            val card = started(FakeBookingRepository(heldBooking = { _, _ -> serverAnswer(sinceHold = 12.minutes) }))
            assertEquals(180, card.uiState.value.secondsLeft)
            // The user looks at the card for a while before tapping it.
            advanceTimeBy(20.seconds)
            runCurrent()
            val route = assertNotNull(card.routeForTap())
            card.onStop()

            // Tapped; the hold screen's own first re-read gets no answer.
            val offline = FakeBookingRepository(heldBooking = { _, _ -> AppResult.Failure(AppError.Network) })
            val hold = HoldViewModel(route, offline, FakeActiveHoldStore(stored), deviceClock())
            hold.onStart()
            runCurrent()
            // Stopped whatever the asserts say: a ticker left running never lets the test end.
            val left = hold.uiState.value.secondsLeft
            val progress = hold.uiState.value.progress
            hold.onStop()

            assertEquals(160, left)
            // The bar still measures against the hold's full 15 minutes.
            assertEquals(160f / 900f, progress, 0.01f)
        }

    @Test
    fun `a hold waiting for payment shows and stays stored`() =
        runViewModelTest {
            val store = FakeActiveHoldStore(stored)
            val pending =
                BookingFixtures.heldBooking(
                    status = "PAYMENT_PENDING",
                    holdExpiresAt = (placedAt + 15.minutes).toString(),
                    serverNow = (placedAt + 5.minutes).toString(),
                )
            val vm = started(FakeBookingRepository(heldBooking = { _, _ -> AppResult.Success(pending) }), store)

            assertTrue(vm.uiState.value.isVisible)
            assertEquals(600, vm.uiState.value.secondsLeft)
            assertEquals(stored, store.hold.value)
            assertTrue(store.clears.isEmpty())
            vm.onStop()
        }

    @Test
    fun `a hold the server calls over is hidden and forgotten`() =
        runViewModelTest {
            for (status in listOf("EXPIRED", "CANCELLED", "CONFIRMED")) {
                val store = FakeActiveHoldStore(stored)
                val repo = FakeBookingRepository(heldBooking = { _, _ -> AppResult.Success(BookingFixtures.heldBooking(status = status)) })
                val vm = started(repo, store)
                advanceUntilIdle()

                assertFalse(vm.uiState.value.isVisible, status)
                assertNull(store.hold.value, status)
            }
        }

    @Test
    fun `an unknown ref or a phone that does not match is hidden and forgotten`() =
        runViewModelTest {
            val store = FakeActiveHoldStore(stored)
            val notFound = AppResult.Failure(AppError.Api(ApiErrorCodes.NOT_FOUND, "Booking not found", 404))
            val vm = started(FakeBookingRepository(heldBooking = { _, _ -> notFound }), store)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isVisible)
            assertNull(store.hold.value)
        }

    @Test
    fun `no answer keeps the stored hold and shows nothing on a guess and asks again`() =
        runViewModelTest {
            val store = FakeActiveHoldStore(stored)
            var online = false
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ -> if (online) serverAnswer(sinceHold = 5.minutes) else AppResult.Failure(AppError.Network) },
                )
            val vm = started(repo, store)

            assertFalse(vm.uiState.value.isVisible)
            assertEquals(stored, store.hold.value)

            online = true
            advanceTimeBy(HoldViewModel.FIRST_RECHECK + HoldViewModel.TICK)
            runCurrent()

            assertTrue(vm.uiState.value.isVisible)
            assertEquals(2, repo.bookingReads.size)
            vm.onStop()
        }

    @Test
    fun `at zero it re-reads and hides once the server says the hold expired`() =
        runViewModelTest {
            val store = FakeActiveHoldStore(stored)
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer(sinceHold = 14.minutes) })
            val vm = started(repo, store)
            assertEquals(60, vm.uiState.value.secondsLeft)

            advanceTimeBy(60.seconds)
            runCurrent()

            assertFalse(vm.uiState.value.isVisible)
            assertEquals(2, repo.bookingReads.size)
            assertNull(store.hold.value)
            // Hidden: the ticker stopped, so no more reads.
            advanceUntilIdle()
            assertEquals(2, repo.bookingReads.size)
        }

    @Test
    fun `every start reads the hold again so one released meanwhile disappears`() =
        runViewModelTest {
            val store = FakeActiveHoldStore(stored)
            var released = false
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ ->
                        if (released) AppResult.Success(BookingFixtures.heldBooking(status = "CANCELLED")) else serverAnswer()
                    },
                )
            val vm = started(repo, store)
            assertTrue(vm.uiState.value.isVisible)

            // The user opens the hold from the card and releases it there.
            vm.onStop()
            released = true
            advanceTimeBy(30.seconds)
            vm.onStart()
            runCurrent()

            assertFalse(vm.uiState.value.isVisible)
            assertEquals(2, repo.bookingReads.size)
        }

    @Test
    fun `it follows the host screen's lifecycle so re-attaching on scroll reads nothing`() =
        runViewModelTest {
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer() })
            val vm = ContinueBookingViewModel(FakeActiveHoldStore(stored), repo, deviceClock())
            val home = TestHost()

            home.registry.currentState = Lifecycle.State.STARTED
            vm.attach(home.registry)
            runCurrent()
            assertEquals(1, repo.bookingReads.size)

            // The card scrolled out of the list and back: composed again, attached again.
            vm.attach(home.registry)
            runCurrent()
            assertEquals(1, repo.bookingReads.size)

            // Home hidden and shown again (back from another screen): read again.
            home.registry.currentState = Lifecycle.State.CREATED
            home.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, repo.bookingReads.size)
            home.registry.currentState = Lifecycle.State.CREATED
        }

    /** Home's back-stack entry, as far as the card's lifecycle goes. */
    private class TestHost : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `back on Home with the read still pending the card counts on from the server's deadline`() =
        runViewModelTest {
            var answering = true
            val repo =
                FakeBookingRepository(
                    heldBooking = { _, _ -> if (answering) serverAnswer(sinceHold = 5.minutes) else awaitCancellation() },
                )
            val vm = started(repo)
            assertEquals(600, vm.uiState.value.secondsLeft)

            // Home hidden for 5 minutes, then shown again on a network that does not answer.
            vm.onStop()
            advanceTimeBy(5.minutes)
            answering = false
            vm.onStart()
            runCurrent()
            // Stopped whatever the asserts say: a ticker left running never lets the test end.
            val left = vm.uiState.value.secondsLeft
            val route = vm.routeForTap()
            vm.onStop()

            assertEquals(300, left)
            // A tap now opens the hold screen at the server's time now, not when Home was hidden.
            assertEquals((placedAt + 10.minutes).toString(), route?.serverNow)
        }

    @Test
    fun `stopped it does not tick and the next start counts from the server again`() =
        runViewModelTest {
            val repo = FakeBookingRepository(heldBooking = { _, _ -> serverAnswer() })
            val vm = started(repo)
            assertEquals(900, vm.uiState.value.secondsLeft)

            vm.onStop()
            advanceTimeBy(5.minutes)
            runCurrent()
            assertEquals(900, vm.uiState.value.secondsLeft)

            vm.onStart()
            runCurrent()
            assertEquals(600, vm.uiState.value.secondsLeft)
            vm.onStop()
        }
}
