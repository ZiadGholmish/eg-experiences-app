package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Between a tapped hold reminder and the held seats (M3-M7) × {ar, en} × {loading, no answer}. A hold found over shows
 * nothing here (the screen leaves and the app's snackbar says the seats were released), so it has no golden.
 *
 * Record: `./gradlew :feature:booking:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h740dp-xhdpi")
class OpenHoldScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
        // The loading view animates: a still clock keeps the golden fixed.
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun loadingArabic() = shoot("open_hold_loading", AppLanguage.ARABIC, OpenHoldUiState())

    @Test
    fun loadingEnglish() = shoot("open_hold_loading", AppLanguage.ENGLISH, OpenHoldUiState())

    @Test
    fun offlineArabic() = shoot("open_hold_offline", AppLanguage.ARABIC, OpenHoldUiState(isLoading = false, error = AppError.Network))

    @Test
    fun offlineEnglish() = shoot("open_hold_offline", AppLanguage.ENGLISH, OpenHoldUiState(isLoading = false, error = AppError.Network))

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        state: OpenHoldUiState,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English, reducedMotion = true) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        OpenHoldContent(state = state, onRetry = {}, onBack = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private companion object {
        const val SETTLE_MS = 500L
    }
}
