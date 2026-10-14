package eg.bahr.feature.auth.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.auth.data.FakeAuthRepository
import eg.bahr.feature.auth.data.ProviderResult
import eg.bahr.feature.auth.model.AuthProvider
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Account deletion (M3-M0): Apple re-authentication first where it applies, and its code sent along. */
class DeleteAccountViewModelTest {
    private fun signedIn(vararg providers: String) = SessionState.SignedIn("Sara", profileComplete = true, providers = providers.toSet())

    private fun vm(repository: FakeAuthRepository) =
        DeleteAccountViewModel(repository, nonces = {
            "raw-nonce-0123456789"
        }, hash = { "sha($it)" })

    @Test
    fun `a Google account is deleted at once - with no Apple code`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn("google"))
            val vm = vm(repository)

            vm.confirm(appleAvailable = true)
            advanceUntilIdle()

            assertEquals(listOf<String?>(null), repository.deletions)
            assertTrue(vm.uiState.value.done)
        }

    @Test
    fun `an Apple account re-authenticates with Apple and sends the authorization code`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn("apple"))
            val vm = vm(repository)

            vm.confirm(appleAvailable = true)
            val request = vm.uiState.value.request!!
            assertEquals(AuthProvider.Apple, request.provider)
            assertTrue(repository.deletions.isEmpty(), "nothing deleted before Apple answers")

            vm.onProviderResult(request, ProviderResult.Success(idToken = "apple.jwt", authorizationCode = "c0de"))
            advanceUntilIdle()

            assertEquals(listOf<String?>("c0de"), repository.deletions)
            assertTrue(vm.uiState.value.done)
        }

    @Test
    fun `cancelling Apple's sheet deletes nothing`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn("apple"))
            val vm = vm(repository)
            vm.confirm(appleAvailable = true)

            vm.onProviderResult(vm.uiState.value.request!!, ProviderResult.Cancelled)
            advanceUntilIdle()

            assertTrue(repository.deletions.isEmpty())
            assertFalse(vm.uiState.value.deleting)
            assertFalse(vm.uiState.value.done)
        }

    @Test
    fun `a failed Apple sheet still deletes - without the code`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn("apple"))
            val vm = vm(repository)
            vm.confirm(appleAvailable = true)

            vm.onProviderResult(vm.uiState.value.request!!, ProviderResult.Failed("no network"))
            advanceUntilIdle()

            assertEquals(listOf<String?>(null), repository.deletions)
        }

    @Test
    fun `without Apple on this device an Apple account is deleted without the code`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn("apple", "google"))
            val vm = vm(repository)

            vm.confirm(appleAvailable = false)
            advanceUntilIdle()

            assertNull(vm.uiState.value.request)
            assertEquals(listOf<String?>(null), repository.deletions)
        }

    @Test
    fun `a host's account is refused with the code kept for the message`() =
        runViewModelTest {
            val conflict = AppError.Api(ApiErrorCodes.CONFLICT, "host", 409)
            val vm = vm(FakeAuthRepository(initial = signedIn("google"), deleteAccount = { AppResult.Failure(conflict) }))

            vm.confirm(appleAvailable = false)
            advanceUntilIdle()

            assertEquals(conflict, vm.uiState.value.error)
            assertFalse(vm.uiState.value.done)
        }
}
