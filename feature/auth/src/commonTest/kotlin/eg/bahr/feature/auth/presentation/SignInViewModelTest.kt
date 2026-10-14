package eg.bahr.feature.auth.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.auth.data.FakeAuthRepository
import eg.bahr.feature.auth.data.FakeAuthRepository.Companion.account
import eg.bahr.feature.auth.data.ProviderResult
import eg.bahr.feature.auth.model.AuthProvider
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sign-in sheet (M3-M0): the provider gets the nonce's hash and our backend the raw nonce; a first
 * sign-in asks for name and phone; a cancelled provider sheet is no error; errors branch on the code.
 */
class SignInViewModelTest {
    private fun vm(repository: FakeAuthRepository) = SignInViewModel(repository, nonces = { RAW }, hash = { "sha($it)" })

    private fun SignInViewModel.provide(result: ProviderResult) = onProviderResult(uiState.value.request!!, result)

    @Test
    fun `a tap asks the screen to run the provider with the hashed nonce`() =
        runViewModelTest {
            val vm = vm(FakeAuthRepository())

            vm.start(AuthProvider.Google)

            val request = vm.uiState.value.request!!
            assertEquals(AuthProvider.Google, request.provider)
            assertEquals("sha($RAW)", request.hashedNonce)
            assertEquals(AuthProvider.Google, vm.uiState.value.busy)
        }

    @Test
    fun `the provider token goes to the backend with the raw nonce and Apple's name`() =
        runViewModelTest {
            val repository = FakeAuthRepository(signIn = { _, _, _, _ -> AppResult.Success(account(profileComplete = true)) })
            val vm = vm(repository)

            vm.start(AuthProvider.Apple)
            vm.provide(ProviderResult.Success(idToken = "apple.jwt", fullName = "Sara Ali"))
            advanceUntilIdle()

            assertEquals(FakeAuthRepository.SignInCall(AuthProvider.Apple, "apple.jwt", RAW, "Sara Ali"), repository.signIns.single())
            assertEquals(SignInUiState.Step.Done, vm.uiState.value.step)
        }

    @Test
    fun `a first sign-in asks for name and phone - prefilled with what the account has`() =
        runViewModelTest {
            val repository = FakeAuthRepository(signIn = { _, _, _, _ -> AppResult.Success(account(name = "Sara")) })
            val vm = vm(repository)

            vm.start(AuthProvider.Google)
            vm.provide(ProviderResult.Success("google.jwt"))
            advanceUntilIdle()

            assertEquals(SignInUiState.Step.Profile, vm.uiState.value.step)
            assertEquals("Sara", vm.uiState.value.name)
            assertFalse(vm.uiState.value.canSave, "no phone yet")

            vm.setPhone("010 1234 5678")
            assertEquals("01012345678", vm.uiState.value.phone)
            vm.saveProfile()
            advanceUntilIdle()

            assertEquals("Sara" to "01012345678", repository.profiles.single())
            assertEquals(SignInUiState.Step.Done, vm.uiState.value.step)
        }

    @Test
    fun `the profile can be left for later`() =
        runViewModelTest {
            val repository = FakeAuthRepository()
            val vm = vm(repository)
            vm.start(AuthProvider.Google)
            vm.provide(ProviderResult.Success("google.jwt"))
            advanceUntilIdle()

            vm.skipProfile()

            assertEquals(SignInUiState.Step.Done, vm.uiState.value.step)
            assertTrue(repository.profiles.isEmpty())
        }

    @Test
    fun `a cancelled provider sheet is no error and frees the buttons`() =
        runViewModelTest {
            val repository = FakeAuthRepository()
            val vm = vm(repository)
            vm.start(AuthProvider.Google)

            vm.provide(ProviderResult.Cancelled)

            assertNull(vm.uiState.value.busy)
            assertFalse(vm.uiState.value.failed)
            assertNull(vm.uiState.value.error)
            assertTrue(repository.signIns.isEmpty())
        }

    @Test
    fun `a refused token keeps the sheet open with the error code`() =
        runViewModelTest {
            val refused = AppError.Api(ApiErrorCodes.UNAUTHORIZED, "no", 401)
            val vm = vm(FakeAuthRepository(signIn = { _, _, _, _ -> AppResult.Failure(refused) }))
            vm.start(AuthProvider.Google)
            vm.provide(ProviderResult.Success("google.jwt"))
            advanceUntilIdle()

            assertEquals(refused, vm.uiState.value.error)
            assertEquals(SignInUiState.Step.Choose, vm.uiState.value.step)
            assertNull(vm.uiState.value.busy)
        }

    @Test
    fun `a stale provider answer is ignored and a second tap while busy does nothing`() =
        runViewModelTest {
            val repository = FakeAuthRepository()
            val vm = vm(repository)
            vm.start(AuthProvider.Google)
            val first = vm.uiState.value.request!!

            vm.start(AuthProvider.Apple)
            assertEquals(first, vm.uiState.value.request, "busy: the second tap is ignored")

            vm.onProviderResult(first.copy(rawNonce = "other"), ProviderResult.Success("x"))
            advanceUntilIdle()
            assertTrue(repository.signIns.isEmpty())
        }

    @Test
    fun `opened while signed in with an incomplete profile goes straight to the profile`() =
        runViewModelTest {
            val vm = vm(FakeAuthRepository(initial = SessionState.SignedIn("Sara", profileComplete = false, providers = setOf("google"))))

            assertEquals(SignInUiState.Step.Profile, vm.uiState.value.step)
            assertEquals("Sara", vm.uiState.value.name)
        }

    private companion object {
        const val RAW = "0123456789abcdef0123456789abcdef"
    }
}
