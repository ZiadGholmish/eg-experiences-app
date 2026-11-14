package eg.bahr.feature.hostworkspace.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.hostworkspace.data.FakeHostWorkspaceRepository
import eg.bahr.feature.hostworkspace.data.HostSignInOutcome
import eg.bahr.feature.hostworkspace.data.apiError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M5-M1: a host's sign-in, the forced password change, and its refusals (one message for every refused sign-in). */
@OptIn(ExperimentalCoroutinesApi::class)
class HostSignInViewModelTest {
    private fun signedOut(vararg overrides: (FakeHostWorkspaceRepository) -> Unit) =
        FakeHostWorkspaceRepository(initial = SessionState.SignedOut).also { repo -> overrides.forEach { it(repo) } }

    @Test
    fun `empty fields are not sent`() =
        runViewModelTest {
            val repository = signedOut()
            val vm = HostSignInViewModel(repository)
            vm.signIn()
            runCurrent()
            assertTrue(repository.signIns.isEmpty())
            assertTrue(vm.uiState.value.checked && vm.uiState.value.emailMissing && vm.uiState.value.passwordMissing)
        }

    @Test
    fun `a sign-in keeps host modes session and forgets the password`() =
        runViewModelTest {
            val repository = signedOut()
            val vm = HostSignInViewModel(repository)
            vm.onEmail(" ashraf@example.com ")
            vm.onPassword("one-time-secret")
            vm.signIn()
            runCurrent()
            assertEquals(listOf(" ashraf@example.com " to "one-time-secret"), repository.signIns)
            assertIs<SessionState.SignedIn>(repository.session.value)
            assertEquals("", vm.uiState.value.password)
        }

    @Test
    fun `a refused sign-in says one thing whatever the reason - and clears the password`() =
        runViewModelTest {
            val repository = signedOut({ it.signIn = { _, _ -> apiError(ApiErrorCodes.UNAUTHORIZED, 401) } })
            val vm = HostSignInViewModel(repository)
            vm.onEmail("ashraf@example.com")
            vm.onPassword("wrong")
            vm.signIn()
            runCurrent()
            assertEquals(HostSignInProblem.Refused, vm.uiState.value.problem)
            assertEquals("", vm.uiState.value.password)
            assertEquals(false, vm.uiState.value.checked, "no 'enter your password' under the refusal")
            assertIs<SessionState.SignedOut>(repository.session.value)
        }

    @Test
    fun `too many tries is said as such`() =
        runViewModelTest {
            val vm = HostSignInViewModel(signedOut({ it.signIn = { _, _ -> apiError(ApiErrorCodes.RATE_LIMITED, 429) } }))
            vm.onEmail("a@b.c")
            vm.onPassword("x")
            vm.signIn()
            runCurrent()
            assertEquals(HostSignInProblem.RateLimited, vm.uiState.value.problem)
        }

    @Test
    fun `a new login changes its password first - with the change token - then is signed in`() =
        runViewModelTest {
            val repository =
                signedOut({
                    it.signIn = {
                        _,
                        _,
                        ->
                        AppResult.Success(HostSignInOutcome.PasswordChangeRequired("change-token"))
                    }
                })
            val vm = HostSignInViewModel(repository)
            vm.onEmail("ashraf@example.com")
            vm.onPassword("one-time")
            vm.signIn()
            runCurrent()
            assertEquals(HostSignInStep.ChangePassword, vm.uiState.value.step)
            assertIs<SessionState.SignedOut>(repository.session.value, "nothing is signed in before the change")

            vm.onNewPassword("short")
            vm.onConfirmPassword("short")
            vm.changePassword()
            runCurrent()
            assertTrue(repository.changes.isEmpty(), "under 12 characters is not sent")

            vm.onNewPassword("a-long-new-password")
            vm.onConfirmPassword("a-long-new-passwordX")
            vm.changePassword()
            runCurrent()
            assertTrue(repository.changes.isEmpty(), "two different passwords are not sent")

            vm.onConfirmPassword("a-long-new-password")
            vm.changePassword()
            runCurrent()
            assertEquals(listOf("change-token" to "a-long-new-password"), repository.changes)
            assertIs<SessionState.SignedIn>(repository.session.value)
            assertEquals(HostSignInStep.Credentials, vm.uiState.value.step)
            assertEquals("", vm.uiState.value.newPassword)
        }

    @Test
    fun `an expired change token goes back to the sign-in`() =
        runViewModelTest {
            val repository =
                signedOut(
                    { it.signIn = { _, _ -> AppResult.Success(HostSignInOutcome.PasswordChangeRequired("t")) } },
                    { it.changePassword = { _, _ -> apiError(ApiErrorCodes.UNAUTHORIZED, 401) } },
                )
            val vm = HostSignInViewModel(repository)
            vm.onEmail("a@b.c")
            vm.onPassword("p")
            vm.signIn()
            runCurrent()
            vm.onNewPassword("a-long-new-password")
            vm.onConfirmPassword("a-long-new-password")
            vm.changePassword()
            runCurrent()
            assertEquals(HostSignInStep.Credentials, vm.uiState.value.step)
            assertEquals(HostSignInProblem.ChangeExpired, vm.uiState.value.problem)
        }

    @Test
    fun `a refused new password stays on the change step`() =
        runViewModelTest {
            val repository =
                signedOut(
                    { it.signIn = { _, _ -> AppResult.Success(HostSignInOutcome.PasswordChangeRequired("t")) } },
                    { it.changePassword = { _, _ -> apiError(ApiErrorCodes.VALIDATION_FAILED, 400) } },
                )
            val vm = HostSignInViewModel(repository)
            vm.onEmail("a@b.c")
            vm.onPassword("p")
            vm.signIn()
            runCurrent()
            vm.onNewPassword("the-one-time-pw")
            vm.onConfirmPassword("the-one-time-pw")
            vm.changePassword()
            runCurrent()
            assertEquals(HostSignInStep.ChangePassword, vm.uiState.value.step)
            assertEquals(HostSignInProblem.PasswordRejected, vm.uiState.value.problem)
        }

    @Test
    fun `no network shows the apps own message`() =
        runViewModelTest {
            val vm = HostSignInViewModel(signedOut({ it.signIn = { _, _ -> AppResult.Failure(AppError.Network) } }))
            vm.onEmail("a@b.c")
            vm.onPassword("p")
            vm.signIn()
            runCurrent()
            assertNull(vm.uiState.value.problem)
            assertEquals(AppError.Network, vm.uiState.value.error)
        }
}
