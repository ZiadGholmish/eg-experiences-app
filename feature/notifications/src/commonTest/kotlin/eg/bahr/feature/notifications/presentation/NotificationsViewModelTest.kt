package eg.bahr.feature.notifications.presentation

import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.notifications.data.FakeNotificationsRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Alerts tab (M3-M0b): nothing until the stored sign-in is read, then follows the session. */
class NotificationsViewModelTest {
    private val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))

    @Test
    fun `reads the stored sign-in - Unknown until then`() =
        runViewModelTest {
            val repository = FakeNotificationsRepository(loaded = signedIn)
            val vm = NotificationsViewModel(repository)
            assertEquals(SessionState.Unknown, vm.uiState.value.session)

            advanceUntilIdle()

            assertEquals(signedIn, vm.uiState.value.session)
        }

    @Test
    fun `a sign-in or sign-out anywhere reaches the tab`() =
        runViewModelTest {
            val repository = FakeNotificationsRepository()
            val vm = NotificationsViewModel(repository)
            advanceUntilIdle()
            assertEquals(SessionState.SignedOut, vm.uiState.value.session)

            repository.state.value = signedIn
            advanceUntilIdle()
            assertEquals(signedIn, vm.uiState.value.session)

            repository.state.value = SessionState.SignedOut
            advanceUntilIdle()
            assertEquals(SessionState.SignedOut, vm.uiState.value.session)
        }
}
