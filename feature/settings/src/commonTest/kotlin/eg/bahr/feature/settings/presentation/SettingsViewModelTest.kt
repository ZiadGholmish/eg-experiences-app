package eg.bahr.feature.settings.presentation

import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.settings.data.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M4-M8: Settings shows the stored language and writes a new pick, once, to the store. */
class SettingsViewModelTest {
    @Test
    fun `shows nothing picked until the store answers - then the stored language`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(initial = null)
            val vm = SettingsViewModel(repository, AppErrorController())
            advanceUntilIdle()
            assertNull(vm.uiState.value.language)

            repository.stored.value = AppLanguage.ENGLISH
            advanceUntilIdle()
            assertEquals(AppLanguage.ENGLISH, vm.uiState.value.language)
        }

    @Test
    fun `picking the other language stores it - and the state follows the store`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(initial = AppLanguage.ARABIC)
            val vm = SettingsViewModel(repository, AppErrorController())
            advanceUntilIdle()

            vm.selectLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()

            assertEquals(listOf(AppLanguage.ENGLISH), repository.writes)
            assertEquals(AppLanguage.ENGLISH, repository.stored.value)
            assertEquals(AppLanguage.ENGLISH, vm.uiState.value.language)
        }

    @Test
    fun `picking the language already shown writes nothing`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(initial = AppLanguage.ARABIC)
            val vm = SettingsViewModel(repository, AppErrorController())
            advanceUntilIdle()

            vm.selectLanguage(AppLanguage.ARABIC)
            advanceUntilIdle()

            assertTrue(repository.writes.isEmpty())
        }

    @Test
    fun `a failed write keeps the old language - and reports the error once`() =
        runViewModelTest {
            val repository = FakeSettingsRepository(initial = AppLanguage.ARABIC, failWith = AppError.Unknown("disk full"))
            val errors = AppErrorController()
            val vm = SettingsViewModel(repository, errors)
            advanceUntilIdle()

            vm.selectLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()

            assertEquals(AppLanguage.ARABIC, vm.uiState.value.language)
            assertEquals(AppError.Unknown("disk full"), errors.current.first()?.error)
        }
}
