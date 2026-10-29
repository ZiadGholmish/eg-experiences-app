package eg.bahr.feature.notifications.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The push pre-prompt sheet (M3-M5, handoff §3; the prototype's "Pre-prompt sheet") × {ar, en} × {light, dark}. The
 * sheet's inside at the foot of the screen, as it rises (a sheet is a second window the tests cannot draw).
 *
 * Record: `./gradlew :feature:notifications:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class PushPromptScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun arabic() = shoot("push_prompt", AppLanguage.ARABIC)

    @Test
    fun english() = shoot("push_prompt", AppLanguage.ENGLISH)

    @Test
    fun darkArabic() = shoot("push_prompt_dark", AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun darkEnglish() = shoot("push_prompt_dark", AppLanguage.ENGLISH, BahrThemeName.Dark)

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                    theme = theme,
                    reducedMotion = true,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
                        PushPromptContent()
                    }
                }
            }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }
}
