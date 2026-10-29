package eg.bahr.feature.notifications.presentation

import eg.bahr.core.common.push.PushChannel
import eg.bahr.core.datastore.PushPromptStore
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.notifications.data.PushPermissionStatus
import eg.bahr.feature.notifications.data.PushPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The push pre-prompt (M3-M5, handoff §3): when it asks, what "Yes" does in each permission state (the OS dialog, or
 * the app's settings when refused for good), and "Not now" counted up to the two after which it stops asking.
 */
class PushPromptViewModelTest {
    private class FakePush(
        override val isAvailable: Boolean = true,
    ) : PushChannel {
        var refreshes = 0
        override val token: StateFlow<String?> = MutableStateFlow(null)

        override suspend fun notificationsAllowed() = false

        override fun refresh() {
            refreshes++
        }
    }

    private class FakeStore(
        var declines: Int = 0,
        var dialogShown: Boolean = false,
    ) : PushPromptStore {
        override suspend fun declines() = declines

        override suspend fun recordDecline() {
            declines++
        }

        override suspend fun systemDialogShown() = dialogShown

        override suspend fun recordSystemDialogShown() {
            dialogShown = true
        }
    }

    private class FakePermissions(
        var status: PushPermissionStatus,
        val grantOnAsk: Boolean = true,
        /** False: the OS dialog stays up until the test answers it through [answer]. */
        val answerAtOnce: Boolean = true,
    ) : PushPermissions {
        var dialogs = 0
        var settingsOpened = 0
        var answer: ((Boolean) -> Unit)? = null

        override suspend fun status(systemDialogShown: Boolean) = status

        override fun requestSystemDialog(onResult: (Boolean) -> Unit) {
            dialogs++
            if (grantOnAsk) status = PushPermissionStatus.Granted
            if (answerAtOnce) onResult(grantOnAsk) else answer = onResult
        }

        override fun openSettings() {
            settingsOpened++
        }
    }

    @Test
    fun `asks after a join while notifications are not allowed`() =
        runViewModelTest {
            val vm = PushPromptViewModel(FakePush(), FakeStore())

            vm.offer(FakePermissions(PushPermissionStatus.CanAsk))
            assertEquals(PushPromptUiState.Deciding, vm.uiState.value)
            advanceUntilIdle()

            assertEquals(PushPromptUiState.Showing, vm.uiState.value)
        }

    @Test
    fun `never asks when allowed already - after two Not nows - or in a build with push off`() =
        runViewModelTest {
            val cases =
                listOf(
                    PushPromptViewModel(FakePush(), FakeStore()) to PushPermissionStatus.Granted,
                    PushPromptViewModel(FakePush(), FakeStore(declines = 2)) to PushPermissionStatus.CanAsk,
                    PushPromptViewModel(FakePush(isAvailable = false), FakeStore()) to PushPermissionStatus.CanAsk,
                )
            for ((vm, status) in cases) {
                vm.offer(FakePermissions(status))
                advanceUntilIdle()
                assertEquals(PushPromptUiState.Done, vm.uiState.value, "status $status")
            }
        }

    @Test
    fun `Yes shows the OS dialog once and a grant reads the push channel again`() =
        runViewModelTest {
            val push = FakePush()
            val store = FakeStore()
            val permissions = FakePermissions(PushPermissionStatus.CanAsk)
            val vm = PushPromptViewModel(push, store)
            vm.offer(permissions)
            advanceUntilIdle()

            vm.allow(permissions)
            advanceUntilIdle()

            assertEquals(PushPromptUiState.Done, vm.uiState.value)
            assertEquals(1, permissions.dialogs)
            assertEquals(0, permissions.settingsOpened)
            assertTrue(store.dialogShown, "remembered, so a later refusal reads as for good")
            assertEquals(1, push.refreshes, "the token is registered once allowed")
            assertEquals(0, store.declines)
        }

    @Test
    fun `permission denied - Yes opens the app's settings - never the OS dialog`() =
        runViewModelTest {
            val permissions = FakePermissions(PushPermissionStatus.Denied)
            val vm = PushPromptViewModel(FakePush(), FakeStore(dialogShown = true))
            vm.offer(permissions)
            advanceUntilIdle()
            assertEquals(PushPromptUiState.Showing, vm.uiState.value, "denied is still asked about: Yes leads to the settings")

            vm.allow(permissions)
            advanceUntilIdle()

            assertEquals(1, permissions.settingsOpened)
            assertEquals(0, permissions.dialogs)
            assertEquals(PushPromptUiState.Done, vm.uiState.value)
        }

    @Test
    fun `a refused OS dialog is not a Not now - the next join asks again`() =
        runViewModelTest {
            val store = FakeStore()
            val permissions = FakePermissions(PushPermissionStatus.CanAsk, grantOnAsk = false)
            val vm = PushPromptViewModel(FakePush(), store)
            vm.offer(permissions)
            advanceUntilIdle()
            vm.allow(permissions)
            advanceUntilIdle()

            vm.offer(permissions)
            advanceUntilIdle()

            assertEquals(0, store.declines)
            assertEquals(PushPromptUiState.Showing, vm.uiState.value)
        }

    @Test
    fun `Not now asks again at the next join and stops after the second`() =
        runViewModelTest {
            val store = FakeStore()
            val permissions = FakePermissions(PushPermissionStatus.CanAsk)
            val vm = PushPromptViewModel(FakePush(), store)

            repeat(PushPromptViewModel.MAX_DECLINES) { join ->
                vm.offer(permissions)
                advanceUntilIdle()
                assertEquals(PushPromptUiState.Showing, vm.uiState.value, "join ${join + 1}")
                vm.notNow()
                advanceUntilIdle()
                assertEquals(PushPromptUiState.Done, vm.uiState.value)
            }
            vm.offer(permissions)
            advanceUntilIdle()

            assertEquals(2, store.declines)
            assertEquals(PushPromptUiState.Done, vm.uiState.value, "two Not nows: no more asking")
            assertFalse(store.dialogShown)
        }

    @Test
    fun `Yes opens the OS dialog inside the tap - the prompt waits for its answer before it is done`() =
        runViewModelTest {
            val push = FakePush()
            val permissions = FakePermissions(PushPermissionStatus.CanAsk, answerAtOnce = false)
            val vm = PushPromptViewModel(push, FakeStore())
            vm.offer(permissions)
            advanceUntilIdle()

            vm.allow(permissions)

            assertEquals(1, permissions.dialogs, "at once, with nothing to wait for first")
            assertEquals(PushPromptUiState.Waiting, vm.uiState.value)
            permissions.answer?.invoke(true)
            assertEquals(PushPromptUiState.Done, vm.uiState.value)
            assertEquals(1, push.refreshes)
        }

    @Test
    fun `once done is told - the next placement on the same page decides afresh`() =
        runViewModelTest {
            val permissions = FakePermissions(PushPermissionStatus.CanAsk)
            val vm = PushPromptViewModel(FakePush(), FakeStore())
            vm.offer(permissions)
            advanceUntilIdle()
            vm.notNow()

            vm.finished()

            assertEquals(PushPromptUiState.Deciding, vm.uiState.value, "not an old Done the next sheet would leave on")
            vm.offer(permissions)
            advanceUntilIdle()
            assertEquals(PushPromptUiState.Showing, vm.uiState.value)
        }
}
