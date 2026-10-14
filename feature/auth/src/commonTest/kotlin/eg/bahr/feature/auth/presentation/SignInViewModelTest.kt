package eg.bahr.feature.auth.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.auth.data.FakeAuthRepository
import eg.bahr.feature.auth.data.FakeAuthRepository.Companion.account
import eg.bahr.feature.auth.data.ProfilePrompt
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
 * sign-in asks for name and phone (M3-M0b: in the app-wide sheet); a cancelled provider sheet is no
 * error; errors branch on the code.
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

    /** M3-M0b: the name and phone are asked in the app-wide sheet, so the sign-in itself is done. */
    @Test
    fun `a first sign-in finishes and asks for name and phone in the sheet - prefilled with what the account has`() =
        runViewModelTest {
            val repository = FakeAuthRepository(signIn = { _, _, _, _ -> AppResult.Success(account(name = "Sara")) })
            val vm = vm(repository)

            vm.start(AuthProvider.Google)
            vm.provide(ProviderResult.Success("google.jwt"))
            advanceUntilIdle()

            assertEquals(SignInUiState.Step.Done, vm.uiState.value.step)
            assertEquals(ProfilePrompt(name = "Sara", phone = "", provider = "google"), repository.prompt.value)
        }

    @Test
    fun `a sign-in with a complete profile asks for nothing`() =
        runViewModelTest {
            val repository =
                FakeAuthRepository(signIn = {
                    _,
                    _,
                    _,
                    _,
                    ->
                    AppResult.Success(account(name = "Sara", phone = "01012345678", profileComplete = true))
                })
            val vm = vm(repository)

            vm.start(AuthProvider.Google)
            vm.provide(ProviderResult.Success("google.jwt"))
            advanceUntilIdle()

            assertNull(repository.prompt.value)
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
    fun `opened while signed in with an incomplete profile finishes and asks for the details`() =
        runViewModelTest {
            val repository =
                FakeAuthRepository(initial = SessionState.SignedIn("Sara", profileComplete = false, providers = setOf("google")))
            val vm = vm(repository)

            assertEquals(SignInUiState.Step.Done, vm.uiState.value.step)
            assertEquals("Sara", repository.prompt.value?.name)
        }

    /** M3-M0b: the inline buttons live as long as their tab; after a sign-out they work again. */
    @Test
    fun `after a sign-out the same buttons sign in again`() =
        runViewModelTest {
            val repository = FakeAuthRepository(signIn = { _, _, _, _ -> AppResult.Success(account(profileComplete = true)) })
            val vm = vm(repository)
            vm.start(AuthProvider.Google)
            vm.provide(ProviderResult.Success("google.jwt"))
            advanceUntilIdle()
            assertEquals(SignInUiState.Step.Done, vm.uiState.value.step)

            repository.state.value = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))
            advanceUntilIdle()
            repository.state.value = SessionState.SignedOut
            advanceUntilIdle()
            vm.start(AuthProvider.Google)

            assertEquals(SignInUiState.Step.Choose, vm.uiState.value.step)
            assertEquals(
                AuthProvider.Google,
                vm.uiState.value.request
                    ?.provider,
            )
        }

    private companion object {
        const val RAW = "0123456789abcdef0123456789abcdef"
    }
}
