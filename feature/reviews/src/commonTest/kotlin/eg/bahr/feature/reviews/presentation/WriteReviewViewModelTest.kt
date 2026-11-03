package eg.bahr.feature.reviews.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.reviews.data.FakeWriteReviewRepository
import eg.bahr.feature.reviews.data.ReviewFixtures
import eg.bahr.feature.reviews.data.SubmitRequest
import eg.bahr.feature.reviews.model.MyReviewDto
import eg.bahr.feature.reviews.model.ReviewLimits
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WriteReviewViewModelTest {
    private fun vm(repository: FakeWriteReviewRepository = FakeWriteReviewRepository()) =
        WriteReviewViewModel(REF, "Burullus at dawn", repository)

    private fun api(
        code: String,
        status: Int,
    ) = AppResult.Failure(AppError.Api(code = code, message = "ignored", httpStatus = status))

    @Test
    fun `starts empty with the trip's title - no errors before a first send`() =
        runViewModelTest {
            val state = vm().uiState.value

            assertEquals("Burullus at dawn", state.title)
            assertEquals(0, state.rating)
            assertFalse(state.ratingMissing)
            assertEquals(ReviewLimits.MAX_PHOTOS, state.photoSlotsLeft)
        }

    @Test
    fun `send without stars shows the stars error and sends nothing - picking stars clears it`() =
        runViewModelTest {
            val repository = FakeWriteReviewRepository()
            val vm = vm(repository)

            vm.submit()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.ratingMissing)
            assertTrue(repository.requests.isEmpty())

            vm.setRating(4)
            assertFalse(vm.uiState.value.ratingMissing)
        }

    @Test
    fun `text past the limit is flagged at once and blocks the send`() =
        runViewModelTest {
            val repository = FakeWriteReviewRepository()
            val vm = vm(repository)
            vm.setRating(5)

            vm.setText("x".repeat(ReviewLimits.MAX_TEXT + 1))
            assertTrue(vm.uiState.value.textTooLong)
            vm.submit()
            advanceUntilIdle()

            assertTrue(repository.requests.isEmpty())
            assertNull(vm.uiState.value.outcome)
        }

    @Test
    fun `a star outside 1 to 5 is ignored`() =
        runViewModelTest {
            val vm = vm()
            vm.setRating(0)
            vm.setRating(6)
            assertEquals(0, vm.uiState.value.rating)
        }

    @Test
    fun `sends the stars - the trimmed text - and the photos in order - then shows the thanks`() =
        runViewModelTest {
            val answer = CompletableDeferred<AppResult<MyReviewDto>>()
            val repository = FakeWriteReviewRepository(submit = { answer.await() })
            val vm = vm(repository)
            val a = byteArrayOf(1)
            val b = byteArrayOf(2)
            vm.setRating(4)
            vm.setText("  Lovely boat.  ")
            vm.addPhotos(listOf(a, b))

            vm.submit()
            runCurrent()
            assertTrue(vm.uiState.value.submitting)
            assertFalse(vm.uiState.value.editable)
            // A second tap while sending does not send twice.
            vm.submit()
            runCurrent()

            answer.complete(AppResult.Success(ReviewFixtures.pending()))
            advanceUntilIdle()

            val request = repository.requests.single()
            assertEquals(SubmitRequest(REF, 4, "Lovely boat.", 2), request)
            assertTrue(request.jpegs[0] === a && request.jpegs[1] === b)
            assertFalse(vm.uiState.value.submitting)
            assertEquals(ReviewOutcome.Submitted, vm.uiState.value.outcome)
        }

    @Test
    fun `blank text is sent as no text`() =
        runViewModelTest {
            val repository = FakeWriteReviewRepository()
            val vm = vm(repository)
            vm.setRating(3)
            vm.setText("   \n ")

            vm.submit()
            advanceUntilIdle()

            assertNull(repository.requests.single().text)
        }

    @Test
    fun `photos past the five slots are dropped - a removed one frees its slot`() =
        runViewModelTest {
            val vm = vm()
            vm.addPhotos(List(3) { byteArrayOf(it.toByte()) })
            vm.addPhotos(List(4) { byteArrayOf(it.toByte()) })
            assertEquals(ReviewLimits.MAX_PHOTOS, vm.uiState.value.photos.size)
            assertEquals(0, vm.uiState.value.photoSlotsLeft)

            val second = vm.uiState.value.photos[1]
            vm.removePhoto(second.id)

            assertEquals(ReviewLimits.MAX_PHOTOS - 1, vm.uiState.value.photos.size)
            assertFalse(
                vm.uiState.value.photos
                    .any { it.id == second.id },
            )
            assertEquals(1, vm.uiState.value.photoSlotsLeft)
        }

    @Test
    fun `a photo the picker could not read is said - and goes on the next change`() =
        runViewModelTest {
            val vm = vm()
            vm.addPhotos(listOf(byteArrayOf(1)), failed = 1)
            assertTrue(vm.uiState.value.photoFailed)
            assertEquals(1, vm.uiState.value.photos.size)

            vm.removePhoto(
                vm.uiState.value.photos
                    .single()
                    .id,
            )
            assertFalse(vm.uiState.value.photoFailed)
        }

    @Test
    fun `CONFLICT - the booking has a review already - ends the page as already reviewed`() =
        runViewModelTest {
            val vm = vm(FakeWriteReviewRepository(submit = { api("CONFLICT", 409) }))
            vm.setRating(5)

            vm.submit()
            advanceUntilIdle()

            assertEquals(ReviewOutcome.AlreadyReviewed, vm.uiState.value.outcome)
            assertNull(vm.uiState.value.problem)
        }

    @Test
    fun `each error code maps to its own problem - the form stays to fix and resend`() =
        runViewModelTest {
            val cases =
                listOf(
                    api("FORBIDDEN", 403) to SubmitProblem.NotYet,
                    api("NOT_FOUND", 404) to SubmitProblem.NotFound,
                    api("UNSUPPORTED_MEDIA_TYPE", 415) to SubmitProblem.PhotoType,
                    api("VALIDATION_FAILED", 413) to SubmitProblem.TooLarge,
                )
            cases.forEach { (failure, problem) ->
                val vm = vm(FakeWriteReviewRepository(submit = { failure }))
                vm.setRating(5)
                vm.submit()
                advanceUntilIdle()

                assertEquals(problem, vm.uiState.value.problem, "for ${failure.error}")
                assertNull(vm.uiState.value.outcome)
                assertTrue(vm.uiState.value.editable)
            }
        }

    @Test
    fun `an unmapped code or a dead network falls back to the app's message - and editing clears it`() =
        runViewModelTest {
            listOf(api("VALIDATION_FAILED", 400), api("RATE_LIMITED", 429), api("SOMETHING_NEW", 400), AppResult.Failure(AppError.Network))
                .forEach { failure ->
                    val vm = vm(FakeWriteReviewRepository(submit = { failure }))
                    vm.setRating(5)
                    vm.submit()
                    advanceUntilIdle()

                    assertEquals(SubmitProblem.Other(failure.error), vm.uiState.value.problem)
                    vm.setText("again")
                    assertNull(vm.uiState.value.problem)
                }
        }

    @Test
    fun `a failed send can be sent again once fixed`() =
        runViewModelTest {
            var first = true
            val repository =
                FakeWriteReviewRepository(submit = {
                    if (first) {
                        first = false
                        api("UNSUPPORTED_MEDIA_TYPE", 415)
                    } else {
                        AppResult.Success(ReviewFixtures.pending())
                    }
                })
            val vm = vm(repository)
            vm.setRating(5)
            vm.addPhotos(listOf(byteArrayOf(1)))
            vm.submit()
            advanceUntilIdle()
            assertIs<SubmitProblem.PhotoType>(vm.uiState.value.problem)

            vm.removePhoto(
                vm.uiState.value.photos
                    .single()
                    .id,
            )
            vm.submit()
            advanceUntilIdle()

            assertEquals(2, repository.requests.size)
            assertEquals(0, repository.requests.last().photos)
            assertEquals(ReviewOutcome.Submitted, vm.uiState.value.outcome)
        }

    @Test
    fun `nothing changes once sent`() =
        runViewModelTest {
            val vm = vm()
            vm.setRating(5)
            vm.submit()
            advanceUntilIdle()

            vm.setRating(2)
            vm.setText("late")
            vm.addPhotos(listOf(byteArrayOf(1)))

            val state = vm.uiState.value
            assertEquals(5, state.rating)
            assertEquals("", state.text)
            assertTrue(state.photos.isEmpty())
        }

    private companion object {
        const val REF = "BHR-7Q2K"
    }
}
