package eg.bahr.feature.hostworkspace.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.ApiErrorReasons
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.hostworkspace.data.FakeHostWorkspaceRepository
import eg.bahr.feature.hostworkspace.data.apiError
import eg.bahr.feature.hostworkspace.data.sampleMe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** M5-M1: host mode's shell follows host mode's own session, and says why it cannot open when it cannot. */
@OptIn(ExperimentalCoroutinesApi::class)
class HostHomeViewModelTest {
    @Test
    fun `signed in - the host and the dates load`() =
        runViewModelTest {
            val vm = HostHomeViewModel(FakeHostWorkspaceRepository())
            runCurrent()
            val state = vm.uiState.value
            assertIs<SessionState.SignedIn>(state.session)
            assertEquals("أشرف البحر", assertIs<HostAccess.Ready>(state.access).me.name)
            assertEquals(2, assertIs<HostDepartures.Loaded>(state.departures).items.size)
        }

    @Test
    fun `a server before M5-B5 answers getHostMe with four fields - host mode still opens`() =
        runViewModelTest {
            val vm = HostHomeViewModel(FakeHostWorkspaceRepository(me = { AppResult.Success(sampleMe(full = false)) }))
            runCurrent()
            assertIs<HostAccess.Ready>(vm.uiState.value.access)
        }

    @Test
    fun `signed out - nothing is asked of the server`() =
        runViewModelTest {
            var asked = 0
            val repository =
                FakeHostWorkspaceRepository(initial = SessionState.SignedOut, me = {
                    asked++
                    AppResult.Success(sampleMe())
                })
            val vm = HostHomeViewModel(repository)
            runCurrent()
            assertIs<SessionState.SignedOut>(vm.uiState.value.session)
            assertEquals(0, asked)

            // The sign-in lands: the shell loads.
            repository.signInNow()
            runCurrent()
            assertIs<HostAccess.Ready>(vm.uiState.value.access)
            assertEquals(1, asked)
        }

    @Test
    fun `a suspended host gets the suspended page`() =
        runViewModelTest {
            val vm =
                HostHomeViewModel(
                    FakeHostWorkspaceRepository(me = { apiError(ApiErrorCodes.FORBIDDEN, 403, ApiErrorReasons.HOST_SUSPENDED) }),
                )
            runCurrent()
            assertEquals(HostAccess.Suspended, vm.uiState.value.access)
        }

    @Test
    fun `an account with no approved host is not active`() =
        runViewModelTest {
            val vm = HostHomeViewModel(FakeHostWorkspaceRepository(me = { apiError(ApiErrorCodes.FORBIDDEN, 403) }))
            runCurrent()
            assertEquals(HostAccess.NotActive, vm.uiState.value.access)
        }

    @Test
    fun `no network can be tried again`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository(me = { AppResult.Failure(AppError.Network) })
            val vm = HostHomeViewModel(repository)
            runCurrent()
            assertEquals(HostAccess.Failed(AppError.Network), vm.uiState.value.access)
            repository.me = { AppResult.Success(sampleMe()) }
            vm.refresh()
            runCurrent()
            assertIs<HostAccess.Ready>(vm.uiState.value.access)
        }

    @Test
    fun `signing out ends host modes session only and shows the sign-in`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository()
            val vm = HostHomeViewModel(repository)
            runCurrent()
            vm.signOut()
            runCurrent()
            assertEquals(1, repository.signOuts)
            assertIs<SessionState.SignedOut>(vm.uiState.value.session)
            assertEquals(HostAccess.Loading, vm.uiState.value.access)
        }
}
