package eg.bahr.feature.auth.presentation

import eg.bahr.core.common.phone.MobileProblem
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.auth.data.FakeAuthRepository
import eg.bahr.feature.auth.data.ProfilePrompt
import eg.bahr.feature.auth.model.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Complete your profile" (M3-M0b, handoff §2): shown while a sign-in's ask is open, prefilled; Save
 * checks on Save and then live, sends the name trimmed and the mobile normalised; Later closes it.
 */
class CompleteProfileViewModelTest {
    /** The sheet shows only while signed in (a sign-out closes it, review R3-2). */
    private val signedIn = SessionState.SignedIn("Sara Ali", profileComplete = false, providers = setOf("google"))

    private val ask = ProfilePrompt(name = "Sara Ali", phone = "", provider = "google")

    @Test
    fun `no ask - no sheet - an ask opens it prefilled`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn)
            val vm = CompleteProfileViewModel(repository)
            advanceUntilIdle()
            assertNull(vm.uiState.value.prompt)

            repository.askForProfile(ask.copy(phone = "+20 101 234 5678"))
            advanceUntilIdle()

            assertEquals("Sara Ali", vm.uiState.value.name)
            // A number stored in the international form is shown, and sent, in the national one.
            assertEquals("01012345678", vm.uiState.value.phone)
        }

    @Test
    fun `Save checks first - nothing is sent and the problems show - then re-checks live`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn)
            repository.askForProfile(ask)
            val vm = CompleteProfileViewModel(repository)
            advanceUntilIdle()

            vm.setPhone("0101234567")
            assertNull(vm.uiState.value.phoneProblem, "no error before the first Save")

            vm.save()
            advanceUntilIdle()
            assertEquals(MobileProblem.TooShort(10), vm.uiState.value.phoneProblem)
            assertTrue(repository.profiles.isEmpty())

            // Live from now on: the next digit clears it.
            vm.setPhone("01012345678")
            assertNull(vm.uiState.value.phoneProblem)
            vm.setName(" ")
            assertTrue(vm.uiState.value.nameInvalid)
        }

    @Test
    fun `a valid form is sent trimmed and normalised - the sheet closes and says it is saved`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn)
            repository.askForProfile(ask)
            val vm = CompleteProfileViewModel(repository)
            advanceUntilIdle()

            vm.setName("  Sara Ali ")
            vm.setPhone("٠١٠-١٢٣٤-٥٦٧٨")
            vm.save()
            advanceUntilIdle()

            assertEquals("Sara Ali" to "01012345678", repository.profiles.single())
            assertNull(repository.prompt.value)
            assertNull(vm.uiState.value.prompt)
            // M3-M8b: the sheet is gone, the "saved" snackbar stays until it has been shown.
            assertTrue(vm.uiState.value.saved)
            vm.savedShown()
            assertFalse(vm.uiState.value.saved)
        }

    @Test
    fun `saving shows until the answer - a refusal keeps the sheet with the error`() =
        runViewModelTest {
            val answer = CompletableDeferred<AppResult<AccountDto>>()
            val repository = FakeAuthRepository(initial = signedIn, updateProfile = { _, _ -> answer.await() })
            repository.askForProfile(ask.copy(phone = "01012345678"))
            val vm = CompleteProfileViewModel(repository)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.saving)
            vm.later()
            assertEquals(ask.copy(phone = "01012345678"), repository.prompt.value, "Later waits while saving")

            val refused = AppError.Api(ApiErrorCodes.VALIDATION_FAILED, "bad phone", 400)
            answer.complete(AppResult.Failure(refused))
            advanceUntilIdle()

            assertFalse(vm.uiState.value.saving)
            assertEquals(refused, vm.uiState.value.error)
            assertEquals("01012345678", repository.prompt.value?.phone, "still asking")
            assertFalse(vm.uiState.value.saved, "nothing saved")
        }

    @Test
    fun `Later closes the sheet and sends nothing`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn)
            repository.askForProfile(ask)
            val vm = CompleteProfileViewModel(repository)
            advanceUntilIdle()

            vm.later()
            advanceUntilIdle()

            assertNull(repository.prompt.value)
            assertNull(vm.uiState.value.prompt)
            assertTrue(repository.profiles.isEmpty())
            assertFalse(vm.uiState.value.saved, "Later saves nothing, so says nothing")
        }

    /**
     * Review R2-7: on iOS a tab tapped while the sheet is open moves it to that tab's Compose root, with
     * a new view model; what was typed comes along.
     */
    @Test
    fun `a second sheet for the same ask keeps what was typed - a new ask starts over`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn)
            repository.askForProfile(ask)
            val first = CompleteProfileViewModel(repository)
            advanceUntilIdle()
            first.setName("Sara A.")
            first.setPhone("0101")

            val second = CompleteProfileViewModel(repository)
            advanceUntilIdle()
            assertEquals("Sara A.", second.uiState.value.name)
            assertEquals("0101", second.uiState.value.phone)

            repository.closeProfilePrompt()
            repository.askForProfile(ask)
            val third = CompleteProfileViewModel(repository)
            advanceUntilIdle()
            assertEquals("Sara Ali", third.uiState.value.name)
            assertEquals("", third.uiState.value.phone)
        }

    /**
     * Review R3-1, iOS A -> B -> A: Home's sheet (its view model kept by the tab) is shown again after the
     * text was changed in Map's sheet. It shows Map's text, and its Save sends it.
     */
    @Test
    fun `going back to a tab's sheet shows what was typed in the other tab - and Save sends it`() =
        runViewModelTest {
            val repository = FakeAuthRepository(initial = signedIn)
            repository.askForProfile(ask)
            val home = CompleteProfileViewModel(repository)
            advanceUntilIdle()
            home.setName("Sara")

            val map = CompleteProfileViewModel(repository)
            advanceUntilIdle()
            assertEquals("Sara", map.uiState.value.name)
            map.setName("Sara Ali")
            map.setPhone("01012345678")
            advanceUntilIdle()

            assertEquals("Sara Ali", home.uiState.value.name)
            assertEquals("01012345678", home.uiState.value.phone)
            home.save()
            advanceUntilIdle()
            assertEquals("Sara Ali" to "01012345678", repository.profiles.single())
        }

    /** Review R3-2: the session ends while asking; the ask and the typed phone go with it. */
    @Test
    fun `a sign-out closes the sheet and forgets what was typed`() =
        runViewModelTest {
            val repository =
                FakeAuthRepository(initial = SessionState.SignedIn("Sara", profileComplete = false, providers = setOf("google")))
            repository.askForProfile(ask)
            val vm = CompleteProfileViewModel(repository)
            advanceUntilIdle()
            vm.setPhone("0101")

            repository.state.value = SessionState.SignedOut
            advanceUntilIdle()

            assertNull(repository.prompt.value)
            assertNull(repository.draft.value)
            assertNull(vm.uiState.value.prompt)
        }
}
