package eg.bahr.feature.settings.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.settings.data.FakePublicProfileRepository
import eg.bahr.feature.settings.data.FakePublicProfileRepository.Companion.VISIBILITY
import eg.bahr.feature.settings.data.FakeSettingsRepository
import eg.bahr.feature.settings.model.ProfileVisibilityDto
import eg.bahr.feature.settings.model.UpdateProfileVisibilityRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MP-M2, Account → Public profile: the switches move at once and send only what changed; a failure puts that switch
 * back; answers landing out of order never undo a newer change; reviews stay usable while private (they also decide how
 * the reviews are signed), the other three wait for the master switch; "View" only once the server says public.
 */
class PublicProfileSettingsViewModelTest {
    private val signedIn = FakeSettingsRepository(account = FakeSettingsRepository.SIGNED_IN)

    private fun vm(repository: FakePublicProfileRepository) = PublicProfileSettingsViewModel(repository, signedIn)

    @Test
    fun `reads the switches - private by default - reviews usable - the rest wait`() =
        runViewModelTest {
            val vm = vm(FakePublicProfileRepository())
            advanceUntilIdle()
            val state = vm.uiState.value

            assertEquals(VISIBILITY, state.shown)
            assertFalse(state.canViewProfile)
            assertTrue(state.enabled(ProfileSwitch.Public))
            assertTrue(state.enabled(ProfileSwitch.Reviews))
            assertFalse(state.enabled(ProfileSwitch.PastTrips))
            assertFalse(state.enabled(ProfileSwitch.Likes))
            assertFalse(state.enabled(ProfileSwitch.FullNameAndPhoto))
        }

    @Test
    fun `a disabled switch sends nothing - reviews can be hidden while private`() =
        runViewModelTest {
            val repository = FakePublicProfileRepository()
            val vm = vm(repository)
            advanceUntilIdle()

            vm.toggle(ProfileSwitch.Likes, true)
            vm.toggle(ProfileSwitch.Reviews, false)
            advanceUntilIdle()

            assertEquals(listOf(UpdateProfileVisibilityRequest(showReviews = false)), repository.updates)
            assertEquals(
                false,
                vm.uiState.value.shown
                    ?.showReviews,
            )
        }

    @Test
    fun `turning public moves at once - View waits for the server's answer`() =
        runViewModelTest {
            val repository = FakePublicProfileRepository()
            val gate = CompletableDeferred<AppResult<ProfileVisibilityDto>>()
            repository.gates += gate
            val vm = vm(repository)
            advanceUntilIdle()

            vm.toggle(ProfileSwitch.Public, true)
            advanceUntilIdle()
            assertEquals(
                true,
                vm.uiState.value.shown
                    ?.isPublic,
            )
            assertTrue(vm.uiState.value.enabled(ProfileSwitch.Likes))
            assertFalse(vm.uiState.value.canViewProfile, "not public on the server yet")
            assertEquals(listOf(UpdateProfileVisibilityRequest(isPublic = true)), repository.updates)

            gate.complete(AppResult.Success(VISIBILITY.copy(isPublic = true)))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.canViewProfile)
        }

    @Test
    fun `a failed save puts the switch back and says so once`() =
        runViewModelTest {
            val repository = FakePublicProfileRepository()
            repository.gates += CompletableDeferred(AppResult.Failure(AppError.Network))
            val vm = vm(repository)
            advanceUntilIdle()

            vm.toggle(ProfileSwitch.Public, true)
            advanceUntilIdle()

            assertEquals(
                false,
                vm.uiState.value.shown
                    ?.isPublic,
            )
            assertTrue(vm.uiState.value.saveFailed)
            vm.saveFailedShown()
            assertFalse(vm.uiState.value.saveFailed)
        }

    /** Two quick changes, the first failing after the second landed: only the first goes back. */
    @Test
    fun `two quick toggles - the first fails late - the second stays`() =
        runViewModelTest {
            val repository = FakePublicProfileRepository()
            val first = CompletableDeferred<AppResult<ProfileVisibilityDto>>()
            val second = CompletableDeferred<AppResult<ProfileVisibilityDto>>()
            repository.gates += first
            repository.gates += second
            val vm = vm(repository)
            advanceUntilIdle()

            vm.toggle(ProfileSwitch.Reviews, false)
            vm.toggle(ProfileSwitch.Public, true)
            advanceUntilIdle()
            second.complete(AppResult.Success(VISIBILITY.copy(isPublic = true)))
            advanceUntilIdle()
            first.complete(AppResult.Failure(AppError.Network))
            advanceUntilIdle()

            val shown = vm.uiState.value.shown
            assertEquals(true, shown?.isPublic)
            assertEquals(true, shown?.showReviews, "the failed change is back to the server's value")
            assertTrue(vm.uiState.value.saveFailed)
        }

    /** An older answer landing after a newer one never overwrites it. */
    @Test
    fun `answers out of order - the newest wins`() =
        runViewModelTest {
            val repository = FakePublicProfileRepository()
            val first = CompletableDeferred<AppResult<ProfileVisibilityDto>>()
            val second = CompletableDeferred<AppResult<ProfileVisibilityDto>>()
            repository.gates += first
            repository.gates += second
            val vm = vm(repository)
            advanceUntilIdle()

            vm.toggle(ProfileSwitch.Public, true)
            vm.toggle(ProfileSwitch.Reviews, false)
            advanceUntilIdle()
            second.complete(AppResult.Success(VISIBILITY.copy(isPublic = true, showReviews = false)))
            advanceUntilIdle()
            first.complete(AppResult.Success(VISIBILITY.copy(isPublic = true)))
            advanceUntilIdle()

            assertEquals(VISIBILITY.copy(isPublic = true, showReviews = false), vm.uiState.value.confirmed)
            assertEquals(VISIBILITY.copy(isPublic = true, showReviews = false), vm.uiState.value.shown)
        }

    @Test
    fun `a failed first read is an error - Retry reads again`() =
        runViewModelTest {
            var answer: AppResult<ProfileVisibilityDto> = AppResult.Failure(AppError.Network)
            val vm = vm(FakePublicProfileRepository(visibility = { answer }))
            advanceUntilIdle()
            assertEquals(AppError.Network, vm.uiState.value.error)
            assertNull(vm.uiState.value.shown)

            answer = AppResult.Success(VISIBILITY)
            vm.retry()
            advanceUntilIdle()
            assertEquals(VISIBILITY, vm.uiState.value.shown)
            assertNull(vm.uiState.value.error)
        }

    /** A 401 nothing could cure drops the sign-in (core:network); the page then has nothing to show and leaves. */
    @Test
    fun `signed out meanwhile - the page knows`() =
        runViewModelTest {
            val vm = vm(FakePublicProfileRepository())
            advanceUntilIdle()
            assertFalse(vm.uiState.value.signedOut)

            signedIn.sessionState.value = SessionState.SignedOut
            advanceUntilIdle()
            assertTrue(vm.uiState.value.signedOut)
        }
}
