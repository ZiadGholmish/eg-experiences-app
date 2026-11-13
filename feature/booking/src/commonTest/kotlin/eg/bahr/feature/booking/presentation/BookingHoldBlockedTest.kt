package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.booking.data.BookingFixtures.SLUG
import eg.bahr.feature.booking.data.BookingFixtures.held
import eg.bahr.feature.booking.data.BookingFixtures.saturdays
import eg.bahr.feature.booking.data.BookingFixtures.trip
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * `HOLD_BLOCKED` on the date + party step (MB-M1): its own state, not the generic error; the time left from
 * `retryAfter` against the device clock when the answer came; no way to try again until that time has passed.
 */
class BookingHoldBlockedTest {
    private val deviceStart = Instant.parse("2026-11-13T10:00:00Z")

    /** The device's clock, moving with the test's virtual time. */
    private fun TestScope.deviceClock(): Clock =
        object : Clock {
            override fun now(): Instant = deviceStart + testScheduler.currentTime.milliseconds
        }

    private var blocked = true

    private fun blockedFor(retryAfterSeconds: Long?) =
        FakeBookingRepository(
            tripBySlug = { AppResult.Success(trip()) },
            departuresFor = { AppResult.Success(saturdays()) },
            placeHold = {
                if (blocked) {
                    AppResult.Failure(
                        AppError.Api(ApiErrorCodes.HOLD_BLOCKED, "localised", FORBIDDEN, retryAfterSeconds = retryAfterSeconds),
                    )
                } else {
                    AppResult.Success(held())
                }
            },
        )

    private fun TestScope.blockedViewModel(repo: FakeBookingRepository): BookingViewModel {
        val vm = BookingViewModel(SLUG, "dep-2", repo, FakeActiveHoldStore(), deviceClock())
        runCurrent()
        vm.setGuestName("ندى حسن")
        vm.setGuestPhone("010 1234 5678")
        vm.placeHold()
        runCurrent()
        return vm
    }

    @Test
    fun `a block shows its own state with the time left - not the generic error`() =
        runViewModelTest {
            val vm = blockedViewModel(blockedFor(retryAfterSeconds = 5 * 3_600L))

            val state = vm.uiState.value
            assertNull(state.holdError, "not the generic error line")
            assertEquals(HoldBlock(deviceStart + 5.hours, BlockTimeLeft(BlockUnit.HOURS, 5)), state.holdBlock)
            assertFalse(state.isPlacingHold)
            assertFalse(state.canPlaceHold, "no button to try again")
        }

    @Test
    fun `no edit lifts the block and no hold is sent while it lasts`() =
        runViewModelTest {
            val repo = blockedFor(retryAfterSeconds = 3_600L)
            val vm = blockedViewModel(repo)

            vm.setGuestName("Nada")
            vm.increaseParty()
            vm.selectDeparture("dep-1")
            vm.onHoldEnded(expired = false)
            vm.placeHold()
            runCurrent()

            assertNotNull(vm.uiState.value.holdBlock)
            assertEquals(1, repo.holdRequests.size, "the keyboard's Done cannot send one either")
        }

    @Test
    fun `the time left follows the device clock and the button comes back when it has passed`() =
        runViewModelTest {
            val vm = blockedViewModel(blockedFor(retryAfterSeconds = 3.days.inWholeSeconds))
            assertEquals(
                BlockTimeLeft(BlockUnit.DAYS, 3),
                vm.uiState.value.holdBlock
                    ?.left,
            )

            advanceTimeBy(2.days + 1.minutes)
            runCurrent()
            assertEquals(
                BlockTimeLeft(BlockUnit.HOURS, 24),
                vm.uiState.value.holdBlock
                    ?.left,
            )

            advanceTimeBy(23.hours + 30.minutes)
            runCurrent()
            assertEquals(
                BlockTimeLeft(BlockUnit.MINUTES, 29),
                vm.uiState.value.holdBlock
                    ?.left,
            )

            advanceTimeBy(29.minutes)
            runCurrent()
            assertNull(vm.uiState.value.holdBlock, "the time has passed")
            assertTrue(vm.uiState.value.canPlaceHold)

            blocked = false
            vm.placeHold()
            runCurrent()
            assertNotNull(vm.uiState.value.held, "the next try is the server's to decide, and it took it")
        }

    @Test
    fun `a block without retryAfter still shows - without a time - and keeps the button away`() =
        runViewModelTest {
            val vm = blockedViewModel(blockedFor(retryAfterSeconds = null))
            advanceTimeBy(7.days)

            assertEquals(HoldBlock(until = null, left = null), vm.uiState.value.holdBlock)
            assertFalse(vm.uiState.value.canPlaceHold)
        }

    private companion object {
        const val FORBIDDEN = 403
    }
}
