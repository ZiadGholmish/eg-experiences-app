package eg.bahr.feature.settings.presentation

import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.settings.data.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Profile (M3-M0b; M4-M8's Settings): the stored language, written once per pick; the session; the
 * account's details read on sign-in and on each return, forgotten on sign-out; "Saved" shown once.
 */
class ProfileViewModelTest {
    private val signedIn = FakeSettingsRepository.SIGNED_IN

    private fun vm(repository: FakeSettingsRepository) = ProfileViewModel(repository, AppErrorController())

    @Test
    fun `shows nothing picked until the store answers - then the stored language`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(initial = null)
            val vm = vm(repository)
            advanceUntilIdle()
            assertNull(vm.uiState.value.language)

            repository.stored.value = AppLanguage.ENGLISH
            advanceUntilIdle()
            assertEquals(AppLanguage.ENGLISH, vm.uiState.value.language)
        }

    @Test
    fun `picking the other language stores it - the one shown writes nothing`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(initial = AppLanguage.ARABIC)
            val vm = vm(repository)
            advanceUntilIdle()

            vm.selectLanguage(AppLanguage.ARABIC)
            vm.selectLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()

            assertEquals(listOf(AppLanguage.ENGLISH), repository.writes)
            assertEquals(AppLanguage.ENGLISH, vm.uiState.value.language)
        }

    @Test
    fun `a failed write keeps the old language - and reports the error once`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(initial = AppLanguage.ARABIC, failWith = AppError.Unknown("disk full"))
            val errors = AppErrorController()
            val vm = ProfileViewModel(repository, errors)
            advanceUntilIdle()

            vm.selectLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()

            assertEquals(AppLanguage.ARABIC, vm.uiState.value.language)
            assertEquals(AppError.Unknown("disk full"), errors.current.first()?.error)
        }

    @Test
    fun `signed out - no account is read and sign-out does nothing`() =
        runViewModelTest {
            val repository = FakeSettingsRepository()
            val vm = vm(repository)
            advanceUntilIdle()

            vm.signOut()
            vm.refresh()
            advanceUntilIdle()

            assertEquals(SessionState.SignedOut, vm.uiState.value.session)
            assertEquals(0, repository.meReads)
            assertEquals(0, repository.signOuts)
        }

    @Test
    fun `signed in - the account's details are read - sign-out signs out once and forgets them`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(account = signedIn)
            val vm = vm(repository)
            advanceUntilIdle()
            assertEquals(1, repository.meReads)
            assertEquals(
                "menna.hassan@gmail.com",
                vm.uiState.value.account
                    ?.email,
            )

            vm.signOut()
            vm.signOut()
            advanceUntilIdle()

            assertEquals(1, repository.signOuts)
            assertEquals(SessionState.SignedOut, vm.uiState.value.session)
            assertNull(vm.uiState.value.account, "the next person on this phone must not see it")
        }

    @Test
    fun `a sign-in elsewhere reads the account - each return reads it again`() =
        runViewModelTest {
            val repository = FakeSettingsRepository()
            val vm = vm(repository)
            advanceUntilIdle()

            repository.sessionState.value = signedIn
            advanceUntilIdle()
            assertEquals(1, repository.meReads)

            vm.refresh()
            advanceUntilIdle()
            assertEquals(2, repository.meReads)
        }

    @Test
    fun `an account that cannot be read keeps the session's name - nothing else breaks`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(account = signedIn, me = { AppResult.Failure(AppError.Network) })
            val vm = vm(repository)
            advanceUntilIdle()

            assertNull(vm.uiState.value.account)
            assertEquals("منة الله حسن", (vm.uiState.value.session as SessionState.SignedIn).accountName)
        }

    @Test
    fun `a save's Saved shows once`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(account = signedIn)
            val vm = vm(repository)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.showSaved)

            repository.setSavedNotice(true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.showSaved)

            vm.savedShown()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.showSaved)
        }

    /**
     * M3-M0b review #2: the first-sign-in sheet (feature:auth) saves the name and phone through its own
     * `updateMe`; only the session's summary tells Profile. The account it holds no longer matches, so it
     * is dropped and read again: Personal info and Edit show the saved phone.
     */
    @Test
    fun `an account completed elsewhere is read again - the stale one is not shown meanwhile`() =
        runViewModelTest {
            val incomplete = SessionState.SignedIn("Menna", profileComplete = false, providers = setOf("google"))
            val repository =
                FakeSettingsRepository(
                    account = incomplete,
                    me = { AppResult.Success(FakeSettingsRepository.account(name = "Menna", phone = null)) },
                )
            val vm = vm(repository)
            advanceUntilIdle()
            assertEquals(
                null,
                vm.uiState.value.account
                    ?.phone,
            )
            assertEquals(1, repository.meReads)

            repository.me = { AppResult.Success(FakeSettingsRepository.account(name = "Menna Hassan")) }
            repository.sessionState.value = SessionState.SignedIn("Menna Hassan", profileComplete = true, providers = setOf("google"))
            advanceUntilIdle()

            assertEquals(2, repository.meReads)
            assertEquals(
                "01012345678",
                vm.uiState.value.account
                    ?.phone,
            )
        }

    /** M3-M0b review #9: the repository forgets the account when the session ends, whoever is listening. */
    @Test
    fun `a session ended elsewhere hides the account at once`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(account = signedIn)
            val vm = vm(repository)
            advanceUntilIdle()
            assertEquals(
                "menna.hassan@gmail.com",
                vm.uiState.value.account
                    ?.email,
            )

            repository.sessionState.value = SessionState.SignedOut
            advanceUntilIdle()

            assertNull(vm.uiState.value.account)
            assertNull(repository.account.first())
        }

    /** M3-M0b (user decision 2026-10-14): Light until picked; a pick is stored once, the one shown is not. */
    @Test
    fun `Appearance shows Light first - a pick is stored once`() =
        runViewModelTest {
            val repository = FakeSettingsRepository()
            val vm = vm(repository)
            advanceUntilIdle()
            assertEquals(AppAppearance.LIGHT, vm.uiState.value.appearance)

            vm.selectAppearance(AppAppearance.LIGHT)
            vm.selectAppearance(AppAppearance.DARK)
            advanceUntilIdle()

            assertEquals(listOf(AppAppearance.DARK), repository.appearanceWrites)
            assertEquals(AppAppearance.DARK, vm.uiState.value.appearance)
        }
}
