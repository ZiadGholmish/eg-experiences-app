package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.data.RecordingHoldReminderSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A tap on the hold reminder (M3-M7): the stored hold and the booking are read again before the held seats open, the
 * seats are never assumed gone on no answer, and a hold found over says so once and is forgotten with its reminder.
 */
class OpenHoldViewModelTest {
    private val stored = StoredHold("BRL-7K4M2X9P", "+201012345678", "2026-10-09T22:42:30+03:00", "2026-10-09T22:27:30+03:00")
    private val errors = AppErrorController()
    private val reminders = RecordingHoldReminderSchedule()

    private fun vm(
        repo: FakeBookingRepository,
        store: FakeActiveHoldStore = FakeActiveHoldStore(stored),
        ref: String = stored.ref,
    ) = OpenHoldViewModel(ref, repo, store, errors, reminders)

    @Test
    fun `a live hold opens the held seats with the stored phone and the fresh deadline`() =
        runViewModelTest {
            val fresh = BookingFixtures.heldBooking(holdExpiresAt = "2026-10-09T22:42:30+03:00", serverNow = "2026-10-09T22:37:00+03:00")
            val repo = FakeBookingRepository(heldBooking = { _, _ -> AppResult.Success(fresh) })
            val vm = vm(repo)
            advanceUntilIdle()

            val route = assertNotNull(vm.uiState.value.open)
            assertEquals(stored.ref, route.ref)
            assertEquals(stored.guestPhone, route.guestPhone)
            assertEquals("2026-10-09T22:37:00+03:00", route.serverNow)
            assertEquals(listOf(stored.ref to stored.guestPhone), repo.bookingReads)
            assertNull(errors.current.first())

            vm.onHandled()
            assertNull(vm.uiState.value.open)
        }

    @Test
    fun `a hold the server calls over ends - says so once - and is forgotten with its reminder`() =
        runViewModelTest {
            for (answer in listOf(
                AppResult.Success(BookingFixtures.heldBooking(status = "EXPIRED")),
                AppResult.Failure(AppError.Api(ApiErrorCodes.NOT_FOUND, null, 404)),
            )) {
                val store = FakeActiveHoldStore(stored)
                val vm = vm(FakeBookingRepository(heldBooking = { _, _ -> answer }), store)
                advanceUntilIdle()

                assertTrue(vm.uiState.value.ended, "$answer")
                assertNull(store.hold.value, "$answer")
                val shown = assertNotNull(errors.current.first())
                assertEquals(ApiErrorCodes.HOLD_EXPIRED, (shown.error as AppError.Api).code)
                errors.dismiss(shown.id)
            }
            assertEquals(listOf(stored.ref, stored.ref), reminders.ended)
        }

    @Test
    fun `a reminder for a hold this device no longer keeps ends without reading`() =
        runViewModelTest {
            val repo = FakeBookingRepository()
            val vm = vm(repo, store = FakeActiveHoldStore(stored), ref = "BRL-OTHER001")
            advanceUntilIdle()

            assertTrue(vm.uiState.value.ended)
            assertTrue(repo.bookingReads.isEmpty())
            assertEquals(listOf("BRL-OTHER001"), reminders.ended)
        }

    @Test
    fun `no answer keeps the hold and offers to try again`() =
        runViewModelTest {
            var answer: AppResult<eg.bahr.feature.booking.model.BookingDto> = AppResult.Failure(AppError.Network)
            val store = FakeActiveHoldStore(stored)
            val vm = vm(FakeBookingRepository(heldBooking = { _, _ -> answer }), store)
            advanceUntilIdle()

            assertEquals(AppError.Network, vm.uiState.value.error)
            assertFalse(vm.uiState.value.ended)
            assertEquals(stored, store.hold.value)
            assertNull(errors.current.first())

            answer = AppResult.Success(BookingFixtures.heldBooking(holdExpiresAt = "2026-10-09T22:42:30+03:00"))
            vm.load()
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.open)
        }
}
