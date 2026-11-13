package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.BookingReviewDto
import eg.bahr.feature.booking.model.rateable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ticket's read (MV-M13): a return to the screen reads the booking again quietly, keeping what is shown while the
 * new read is on its way and when it fails, so a review just sent takes "Rate your trip" away without a skeleton.
 */
class BookingConfirmedViewModelTest {
    private val ref = "BRL-7K4M2X9P"

    @Test
    fun `a refresh replaces the booking without showing the skeleton`() =
        runViewModelTest {
            var booking = BookingFixtures.heldBooking(status = "COMPLETED", canReview = true)
            val vm = BookingConfirmedViewModel(ref, FakeBookingRepository(bookingByRef = { AppResult.Success(booking) }))
            advanceUntilIdle()
            assertTrue(assertNotNull(vm.uiState.value.booking).rateable)

            booking = booking.copy(canReview = false, review = BookingReviewDto("r1", "pending"))
            vm.refresh()
            assertFalse(vm.uiState.value.isLoading, "no skeleton while it reads again")
            advanceUntilIdle()

            assertFalse(assertNotNull(vm.uiState.value.booking).rateable, "reviewed: no Rate")
        }

    @Test
    fun `a failed refresh keeps the ticket shown`() =
        runViewModelTest {
            val shown = BookingFixtures.heldBooking(status = "CONFIRMED")
            var answer: AppResult<BookingDto> = AppResult.Success(shown)
            val vm = BookingConfirmedViewModel(ref, FakeBookingRepository(bookingByRef = { answer }))
            advanceUntilIdle()

            answer = AppResult.Failure(AppError.Network)
            vm.refresh()
            advanceUntilIdle()

            assertEquals(shown, vm.uiState.value.booking)
            assertNull(vm.uiState.value.error, "the ticket stays, no error over it")
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `a refresh while the first read is on its way does nothing`() =
        runViewModelTest {
            val first = CompletableDeferred<AppResult<BookingDto>>()
            var reads = 0
            val vm =
                BookingConfirmedViewModel(
                    ref,
                    FakeBookingRepository(bookingByRef = {
                        reads++
                        first.await()
                    }),
                )
            advanceUntilIdle()
            vm.refresh()
            advanceUntilIdle()
            assertEquals(1, reads)

            first.complete(AppResult.Success(BookingFixtures.heldBooking(status = "CONFIRMED")))
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.booking)
        }

    @Test
    fun `a refresh over an error reads it again in full`() =
        runViewModelTest {
            var answer: AppResult<BookingDto> = AppResult.Failure(AppError.Network)
            val vm = BookingConfirmedViewModel(ref, FakeBookingRepository(bookingByRef = { answer }))
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.error)

            answer = AppResult.Success(BookingFixtures.heldBooking(status = "CONFIRMED"))
            vm.refresh()
            assertTrue(vm.uiState.value.isLoading)
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertNotNull(vm.uiState.value.booking)
        }
}
