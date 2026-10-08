package eg.bahr.core.designsystem.error

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.error_network
import eg.bahr.core.localization.generated.resources.error_timeout
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class BahrErrorHostTest {
    @get:Rule
    val compose = createComposeRule()

    private val messages = Channel<StringResource>(Channel.UNLIMITED)
    private lateinit var deviceLocale: Locale

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ARABIC) {
                BahrTheme {
                    BahrErrorHost(messages = messages.receiveAsFlow()) {}
                }
            }
        }
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun `a message is shown in the app language, not the device language`() {
        messages.trySend(Res.string.error_network)

        compose.waitUntil { compose.onAllNodesWithTextCount("لا يوجد اتصال. تحقق من الإنترنت وحاول مرة أخرى.") == 1 }
    }

    @Test
    fun `messages queue - the second shows after the first is gone`() {
        messages.trySend(Res.string.error_network)
        messages.trySend(Res.string.error_timeout)

        compose.onNodeWithText("لا يوجد اتصال. تحقق من الإنترنت وحاول مرة أخرى.").assertExists()
        compose.onNodeWithText("استغرق الأمر وقتًا طويلًا. حاول مرة أخرى.").assertDoesNotExist()

        // SnackbarDuration.Short is 4 s; the test clock auto-advances.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTextCount("استغرق الأمر وقتًا طويلًا. حاول مرة أخرى.") == 1
        }
        compose.onNodeWithText("لا يوجد اتصال. تحقق من الإنترنت وحاول مرة أخرى.").assertDoesNotExist()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
    onAllNodes(
        androidx.compose.ui.test
            .hasText(text),
    ).fetchSemanticsNodes().size
