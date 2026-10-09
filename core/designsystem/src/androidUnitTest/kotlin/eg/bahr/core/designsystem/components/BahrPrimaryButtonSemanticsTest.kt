package eg.bahr.core.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * M0-M2 review R2-1: a loading primary button tells a screen reader it is busy, not just disabled,
 * in the app's language.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class BahrPrimaryButtonSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale
    private var loading by mutableStateOf(false)

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    private fun show(language: AppLanguage) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    BahrPrimaryButton(text = LABEL, onClick = {}, loading = loading)
                }
            }
        }
    }

    private fun stateDescription(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `an idle button has no busy state`() {
        show(AppLanguage.ENGLISH)

        compose.onNodeWithText(LABEL).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `a loading button is announced as busy in English`() {
        loading = true
        show(AppLanguage.ENGLISH)

        compose.onNodeWithText(LABEL).assert(stateDescription("Loading")).assertIsNotEnabled()
    }

    @Test
    fun `a loading button is announced as busy in Arabic`() {
        loading = true
        show(AppLanguage.ARABIC)

        compose.onNodeWithText(LABEL).assert(stateDescription("جاري التحميل"))
    }

    private companion object {
        const val LABEL = "Hold seats and pay"
    }
}
