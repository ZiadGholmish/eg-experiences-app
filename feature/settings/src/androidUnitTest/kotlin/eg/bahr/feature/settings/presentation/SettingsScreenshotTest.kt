package eg.bahr.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.settings.data.FakeSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * M4-M8: Settings × {ar, en} × {loaded, store not answered yet}, through the real view model on a fake
 * repository; plus the live switch: a tap on English stores it and the screen, drawn from the stored
 * language as the app's roots are, redraws left to right in English.
 *
 * Record: `./gradlew :feature:settings:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadedArabic() = loaded(AppLanguage.ARABIC)

    @Test
    fun loadedEnglish() = loaded(AppLanguage.ENGLISH)

    private fun loaded(language: AppLanguage) {
        val repository = FakeSettingsRepository(initial = language)
        shoot("settings_loaded", MutableStateFlow(language), SettingsViewModel(repository, AppErrorController()))
    }

    /** The store has not answered: neither language is picked (no guess that could flip). */
    @Test
    fun loadingArabic() = loading(AppLanguage.ARABIC)

    @Test
    fun loadingEnglish() = loading(AppLanguage.ENGLISH)

    private fun loading(language: AppLanguage) {
        val repository = FakeSettingsRepository(initial = null)
        shoot("settings_loading", MutableStateFlow(language), SettingsViewModel(repository, AppErrorController()))
    }

    @Test
    fun `picking English stores it - and the screen redraws in English, left to right`() {
        val repository = FakeSettingsRepository(initial = AppLanguage.ARABIC)
        val vm = SettingsViewModel(repository, AppErrorController())
        // What App does: the stored language decides the composition's language.
        setContent(repository.stored, vm)
        compose.onNodeWithTag(languageTag(AppLanguage.ARABIC)).assertIsSelected()

        compose.onNodeWithTag(languageTag(AppLanguage.ENGLISH)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(AppLanguage.ENGLISH), repository.writes)
        compose.onNodeWithTag(languageTag(AppLanguage.ENGLISH)).assertIsSelected()
        compose.captureScreenshot("settings_switched_to_en")
    }

    // ---------- Plumbing ----------

    private fun shoot(
        prefix: String,
        language: MutableStateFlow<AppLanguage?>,
        vm: SettingsViewModel,
    ) {
        setContent(language, vm)
        compose.captureScreenshot("${prefix}_${language.value?.tag}")
    }

    private fun setContent(
        language: MutableStateFlow<AppLanguage?>,
        vm: SettingsViewModel,
    ) {
        compose.setContent {
            val current = language.collectAsState().value ?: AppLanguage.default
            ProvideAppLanguage(current) {
                BahrTheme(locale = if (current == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SettingsScreen(appVersion = APP_VERSION, viewModel = vm)
                    }
                }
            }
        }
    }

    private companion object {
        const val APP_VERSION = "0.1"
    }
}
