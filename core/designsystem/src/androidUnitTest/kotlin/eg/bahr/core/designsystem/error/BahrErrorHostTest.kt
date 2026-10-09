package eg.bahr.core.designsystem.error

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.error_network
import eg.bahr.core.localization.generated.resources.error_timeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class BahrErrorHostTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    /** Stands in for AppErrorController: the head is shown until the host acknowledges it. */
    private var queue by mutableStateOf(emptyList<BahrErrorMessage>())
    private var language by mutableStateOf(AppLanguage.ARABIC)
    private var hostMounted by mutableStateOf(true)
    private var dismissed = 0

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme {
                    if (hostMounted) {
                        BahrErrorHost(
                            message = queue.firstOrNull(),
                            onDismissed = { shown ->
                                dismissed++
                                queue = queue.filterNot { it == shown }
                            },
                        ) {}
                    }
                }
            }
        }
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun `a message is shown in the app language, not the device language`() {
        queue = listOf(networkError)

        compose.waitUntil { compose.countNodesWithText(NETWORK_AR) == 1 }
    }

    @Test
    fun `messages queue - the second shows after the first is dismissed`() {
        queue = listOf(networkError, timeoutError)

        compose.onNodeWithText(NETWORK_AR).assertExists()
        compose.onNodeWithText(TIMEOUT_AR).assertDoesNotExist()

        // SnackbarDuration.Short is 4 s; the test clock auto-advances.
        compose.waitUntil(timeoutMillis = 10_000) { compose.countNodesWithText(TIMEOUT_AR) == 1 }
        compose.onNodeWithText(NETWORK_AR).assertDoesNotExist()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a language switch keeps the message on screen, now in the new language`() {
        compose.mainClock.autoAdvance = false
        queue = listOf(networkError)
        compose.advanceShortly()
        compose.onNodeWithText(NETWORK_AR).assertExists()

        language = AppLanguage.ENGLISH
        compose.advanceShortly()

        compose.onNodeWithText(NETWORK_EN).assertExists()
        compose.onNodeWithText(NETWORK_AR).assertDoesNotExist()
        assertEquals(0, dismissed, "a torn-down host must not acknowledge the message")
    }

    @Test
    fun `a host rebuilt after being torn down (Activity recreation) shows the message again`() {
        compose.mainClock.autoAdvance = false
        queue = listOf(networkError)
        compose.advanceShortly()
        compose.onNodeWithText(NETWORK_AR).assertExists()

        hostMounted = false
        compose.advanceShortly()
        compose.onNodeWithText(NETWORK_AR).assertDoesNotExist()

        hostMounted = true
        compose.advanceShortly()

        compose.onNodeWithText(NETWORK_AR).assertExists()
        assertEquals(0, dismissed)
    }

    private companion object {
        val networkError = BahrErrorMessage(id = 1, text = Res.string.error_network)
        val timeoutError = BahrErrorMessage(id = 2, text = Res.string.error_timeout)
        const val NETWORK_AR = "لا يوجد اتصال. تحقق من الإنترنت وحاول مرة أخرى."
        const val NETWORK_EN = "No connection. Check your internet and try again."
        const val TIMEOUT_AR = "استغرق الأمر وقتًا طويلًا. حاول مرة أخرى."
    }
}

/** Well inside SnackbarDuration.Short (4 s), so a shown snackbar cannot time out. */
private const val SHORTLY_MS = 500L

/** Recompose on the state change first, then run frames: the effect and the snackbar need both. */
private fun ComposeContentTestRule.advanceShortly() {
    waitForIdle()
    mainClock.advanceTimeBy(SHORTLY_MS)
    waitForIdle()
}

private fun ComposeContentTestRule.countNodesWithText(text: String): Int = onAllNodes(hasText(text)).fetchSemanticsNodes().size
