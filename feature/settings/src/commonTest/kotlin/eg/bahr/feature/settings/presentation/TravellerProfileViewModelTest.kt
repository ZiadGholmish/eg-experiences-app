package eg.bahr.feature.settings.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.settings.data.FakePublicProfileRepository
import eg.bahr.feature.settings.data.FakePublicProfileRepository.Companion.PUBLIC_ID
import eg.bahr.feature.settings.data.FakePublicProfileRepository.Companion.profile
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MP-M2: a traveller's profile: read by its public id, 404 as "not public" with no retry, other failures retryable. */
class TravellerProfileViewModelTest {
    @Test
    fun `reads the profile of its id - loading until it lands`() =
        runViewModelTest {
            val repository = FakePublicProfileRepository()
            val vm = TravellerProfileViewModel(PUBLIC_ID, repository)
            assertTrue(vm.uiState.value.isLoading)

            advanceUntilIdle()
            assertEquals(listOf(PUBLIC_ID), repository.travellerReads)
            assertEquals(profile(), vm.uiState.value.profile)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun `NOT_FOUND is not public - by its code`() =
        runViewModelTest {
            val repository = FakePublicProfileRepository(traveller = { AppResult.Failure(AppError.Api(ApiErrorCodes.NOT_FOUND, "x", 404)) })
            val vm = TravellerProfileViewModel(PUBLIC_ID, repository)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.notAvailable)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `another failure is an error - Retry reads again once per burst`() =
        runViewModelTest {
            var answer: AppResult<eg.bahr.feature.settings.model.TravellerProfileDto> = AppResult.Failure(AppError.Network)
            val repository = FakePublicProfileRepository(traveller = { answer })
            val vm = TravellerProfileViewModel(PUBLIC_ID, repository)
            advanceUntilIdle()
            assertEquals(AppError.Network, vm.uiState.value.error)
            assertFalse(vm.uiState.value.notAvailable)

            answer = AppResult.Success(profile())
            vm.retry()
            vm.retry()
            advanceTimeBy(1)
            advanceUntilIdle()
            assertEquals(2, repository.travellerReads.size)
            assertEquals(profile(), vm.uiState.value.profile)
            assertNull(vm.uiState.value.error)
        }
}
