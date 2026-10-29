package eg.bahr.feature.notifications.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.push.PushChannel
import eg.bahr.core.datastore.PushPromptStore
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.feature.notifications.data.PushPermissionStatus
import eg.bahr.feature.notifications.data.PushPermissions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pre-prompt as the trip page places it (M3-M5): placed while `askForPush` is true and removed on `onDone`.
 * "Yes" must reach the OS dialog while the prompt is still placed (Android's result returns to its launcher), `onDone`
 * must wait for the dialog's answer, and a second placement on the same page (same view model) must ask again.
 */
@RunWith(RobolectricTestRunner::class)
class PushPromptSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private class Push : PushChannel {
        override val isAvailable = true
        override val token: StateFlow<String?> = MutableStateFlow(null)

        override suspend fun notificationsAllowed() = false

        override fun refresh() = Unit
    }

    /** The preferences read suspends (DataStore): here, until the test lets it go. */
    private class Store(
        val gate: CompletableDeferred<Unit>,
    ) : PushPromptStore {
        override suspend fun declines(): Int {
            gate.await()
            return 0
        }

        override suspend fun recordDecline() = Unit

        override suspend fun systemDialogShown(): Boolean {
            gate.await()
            return false
        }

        override suspend fun recordSystemDialogShown() = Unit
    }

    private class Permissions : PushPermissions {
        var answer: ((Boolean) -> Unit)? = null
        var placedWhenAsked: Boolean? = null
        var placed = { false }

        override suspend fun status(systemDialogShown: Boolean) = PushPermissionStatus.CanAsk

        override fun requestSystemDialog(onResult: (Boolean) -> Unit) {
            placedWhenAsked = placed()
            answer = onResult
        }

        override fun openSettings() = Unit
    }

    @Test
    fun `Yes reaches the OS dialog while placed - done waits for its answer - a second join asks again`() {
        val gate = CompletableDeferred<Unit>()
        val vm = PushPromptViewModel(Push(), Store(gate))
        val permissions = Permissions()
        var placed by mutableStateOf(true)
        var doneCount = 0
        permissions.placed = { placed }
        compose.setContent {
            BahrTheme {
                if (placed) {
                    PushPromptSheet(
                        onDone = {
                            doneCount++
                            placed = false
                        },
                        viewModel = vm,
                        permissions = permissions,
                    )
                }
            }
        }

        gate.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithTag(PUSH_PROMPT_ALLOW_TAG).performClick()
        compose.waitForIdle()

        assertEquals("the OS dialog was asked for while the prompt was placed", true, permissions.placedWhenAsked)
        assertEquals("not done before the dialog answers", 0, doneCount)
        assertTrue(placed)

        compose.runOnUiThread { permissions.answer?.invoke(false) }
        compose.waitForIdle()
        assertEquals(1, doneCount)

        compose.runOnUiThread { placed = true }
        compose.waitForIdle()
        compose.onNodeWithTag(PUSH_PROMPT_ALLOW_TAG).assertIsDisplayed()
        assertEquals("the second placement did not leave on the old answer", 1, doneCount)
    }
}
