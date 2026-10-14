package eg.bahr.feature.settings.presentation

import eg.bahr.core.common.phone.MobileProblem
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.settings.data.FakeSettingsRepository
import eg.bahr.feature.settings.data.FakeSettingsRepository.Companion.account
import eg.bahr.feature.settings.model.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edit personal info (M3-M0b, handoff §2): starts from the account Profile read (or reads it), checks
 * on Save and then live, sends trimmed and normalised, says "Saved" through Profile, and keeps the form
 * with the error when the server refuses.
 */
class EditPersonalInfoViewModelTest {
    @Test
    fun `starts from the account already read - the number in its national form`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(account = FakeSettingsRepository.SIGNED_IN)
            repository.accountState.value = account(phone = "+201012345678")
            val vm = EditPersonalInfoViewModel(repository)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.loading)
            assertEquals("منة الله حسن", state.name)
            assertEquals("01012345678", state.phone)
            assertEquals("menna.hassan@gmail.com", state.email)
            assertEquals(0, repository.meReads, "no second read")
        }

    /** M3-M0b review #2: an account that no longer matches the session is read again, not shown. */
    @Test
    fun `a stale account is not used - the account is read again`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(account = SessionState.SignedIn("Menna Hassan", true, setOf("google")))
            repository.accountState.value = account(name = "Menna", phone = null)
            repository.me = { AppResult.Success(account(name = "Menna Hassan")) }
            val vm = EditPersonalInfoViewModel(repository)
            advanceUntilIdle()

            assertEquals(1, repository.meReads)
            assertEquals("01012345678", vm.uiState.value.phone)
        }

    @Test
    fun `with nothing read yet it reads the account - a failure offers to try again`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(me = { AppResult.Failure(AppError.Network) })
            val vm = EditPersonalInfoViewModel(repository)
            advanceUntilIdle()
            assertEquals(AppError.Network, vm.uiState.value.loadError)

            repository.me = { AppResult.Success(account()) }
            vm.load()
            advanceUntilIdle()

            assertNull(vm.uiState.value.loadError)
            assertEquals("01012345678", vm.uiState.value.phone)
        }

    @Test
    fun `Save checks first - then live while the person fixes it`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(account = FakeSettingsRepository.SIGNED_IN)
            repository.accountState.value = account()
            val vm = EditPersonalInfoViewModel(repository)
            advanceUntilIdle()

            vm.setPhone("0101234567")
            assertNull(vm.uiState.value.phoneProblem, "quiet until Save")
            vm.save()
            advanceUntilIdle()

            assertEquals(MobileProblem.TooShort(10), vm.uiState.value.phoneProblem)
            assertTrue(repository.updates.isEmpty())

            vm.setPhone("01312345678")
            assertEquals(MobileProblem.WrongStart, vm.uiState.value.phoneProblem)
            vm.setPhone("01512345678")
            assertNull(vm.uiState.value.phoneProblem)
        }

    @Test
    fun `a valid form is sent trimmed and normalised - and Profile is told to say Saved`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(account = FakeSettingsRepository.SIGNED_IN)
            repository.accountState.value = account()
            val vm = EditPersonalInfoViewModel(repository)
            advanceUntilIdle()

            vm.setName("  Menna Hassan ")
            vm.setPhone("+20 112 345 6789")
            vm.save()
            advanceUntilIdle()

            assertEquals("Menna Hassan" to "01123456789", repository.updates.single())
            assertTrue(vm.uiState.value.saved)
            assertTrue(repository.saved.value)
            assertEquals("Menna Hassan", repository.accountState.value?.name)
        }

    @Test
    fun `saving holds the form - a VALIDATION_FAILED keeps it with the error`() =
        runViewModelTest {
            val answer = CompletableDeferred<AppResult<AccountDto>>()
            val repository = FakeSettingsRepository(account = FakeSettingsRepository.SIGNED_IN, update = { _, _ -> answer.await() })
            repository.accountState.value = account()
            val vm = EditPersonalInfoViewModel(repository)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.saving)
            vm.save()
            advanceUntilIdle()
            assertEquals(1, repository.updates.size, "one save at a time")

            val refused = AppError.Api(ApiErrorCodes.VALIDATION_FAILED, "phone", 400)
            answer.complete(AppResult.Failure(refused))
            advanceUntilIdle()

            assertFalse(vm.uiState.value.saving)
            assertFalse(vm.uiState.value.saved)
            assertEquals(refused, vm.uiState.value.saveError)
            assertFalse(repository.saved.value)
        }
}
