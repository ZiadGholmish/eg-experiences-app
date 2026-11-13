package eg.bahr.core.designsystem.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * MV-M12 (user bug: "can't type 010", the screen jumps on each digit). On Android a key typed on a hardware keyboard
 * (the emulator's, a Bluetooth one, `adb shell input text`) also takes the window out of touch mode. The sheet asked for
 * the focus on its safe action whenever the input mode turned to keyboard, so the first digit typed into
 * "Complete your profile"'s phone field moved the focus to "Later" and the next ones went nowhere. Reproduced on the
 * emulator before the fix (MV-M12 report). The mode switch is driven by hand here, the way a typed key switches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ar")
class BahrBottomSheetFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private var mode by mutableStateOf(InputMode.Touch)
    private var phone by mutableStateOf("")

    private val modes =
        object : InputModeManager {
            override val inputMode: InputMode get() = mode

            override fun requestInputMode(inputMode: InputMode): Boolean {
                mode = inputMode
                return true
            }
        }

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalInputModeManager provides modes) {
                BahrTheme(locale = BahrLocale.Arabic) {
                    BahrBottomSheetContent(
                        BahrBottomSheet.Props(
                            title = "Title",
                            onDismiss = {},
                            actions = listOf(BahrBottomSheet.Action("Save")),
                            cancel = BahrBottomSheet.Action("Later", testTag = CANCEL_TAG),
                        ),
                    ) {
                        BahrFormField(
                            props =
                                BahrFormField.Props(
                                    label = "Mobile",
                                    value = phone,
                                    onValueChange = { phone = it },
                                    leftToRight = true,
                                    keyboard = BahrFormField.Keyboard.Phone,
                                    testTag = FIELD_TAG,
                                ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a key typed into the sheet's field keeps the focus there, every digit of 010 too`() {
        show()
        compose.onNodeWithTag(FIELD_TAG).performClick()
        compose.onNodeWithTag(FIELD_TAG).assertIsFocused()

        "010".forEachIndexed { i, digit ->
            compose.onNodeWithTag(FIELD_TAG).performTextInput(digit.toString())
            // The first key leaves touch mode as it is typed; the sheet then recomposes in keyboard mode.
            if (i == 0) mode = InputMode.Keyboard
            compose.waitForIdle()
            // Checked before the next key: performTextInput focuses its node itself, so it would hide a stolen focus.
            compose.onNodeWithTag(FIELD_TAG).assertIsFocused()
            compose.onNodeWithTag(CANCEL_TAG).assertIsNotFocused()
        }
        assertEquals("010", phone)
    }

    @Test
    fun `with nothing focused the first key still lands on the safe action`() {
        show()
        compose.onNodeWithTag(CANCEL_TAG).assertIsNotFocused()

        mode = InputMode.Keyboard
        compose.waitForIdle()

        compose.onNodeWithTag(CANCEL_TAG).assertIsFocused()
    }

    @Test
    fun `a sheet opened in keyboard mode starts on the safe action`() {
        mode = InputMode.Keyboard
        show()

        compose.onNodeWithTag(CANCEL_TAG).assertIsFocused()
    }

    private companion object {
        const val FIELD_TAG = "field"
        const val CANCEL_TAG = "cancel"
    }
}
