package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
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
 * BahrLottie (M3-M8): each shipped animation re-tinted for {light, dark, HighContrast}, on a fixed frame:
 *  - still (reduced motion: the last whole frame, where a played-once animation stops, or the frame the
 *    animation names, the suitcase's), in ar and en (the animation itself is not mirrored);
 *  - mid-animation, to show the moving parts' colours (the bell's rings, the boat between two rocks, the
 *    check's halo, the suitcase growing).
 *
 * Record: `./gradlew :core:designsystem:recordRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class BahrLottieScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun restingArabic() = shoot("lottie_resting", AppLanguage.ARABIC, BahrThemeName.LakeBurullus)

    @Test
    fun restingEnglish() = shoot("lottie_resting", AppLanguage.ENGLISH, BahrThemeName.LakeBurullus)

    @Test
    fun restingDarkArabic() = shoot("lottie_resting_dark", AppLanguage.ARABIC, BahrThemeName.Dark)

    @Test
    fun restingDarkEnglish() = shoot("lottie_resting_dark", AppLanguage.ENGLISH, BahrThemeName.Dark)

    @Test
    fun restingHighContrast() = shoot("lottie_resting_high_contrast", AppLanguage.ENGLISH, BahrThemeName.HighContrast)

    @Test
    fun midAnimation() = shoot("lottie_mid", AppLanguage.ENGLISH, BahrThemeName.LakeBurullus, MID_PROGRESS)

    @Test
    fun midAnimationDark() = shoot("lottie_mid_dark", AppLanguage.ENGLISH, BahrThemeName.Dark, MID_PROGRESS)

    @Test
    fun midAnimationHighContrast() = shoot("lottie_mid_high_contrast", AppLanguage.ENGLISH, BahrThemeName.HighContrast, MID_PROGRESS)

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        theme: BahrThemeName,
        progress: Float? = null,
    ) {
        compose.setContent { Sample(language, theme, progress) }
        compose.waitUntilLottiesDraw(count = BahrLottie.Animation.entries.size)
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    /** Each animation on a `surface` ground: Alerts' page, the sign-in card, the sheets. */
    @Composable
    private fun Sample(
        language: AppLanguage,
        theme: BahrThemeName,
        progress: Float?,
    ) {
        ProvideAppLanguage(language) {
            BahrTheme(
                locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                theme = theme,
                reducedMotion = true,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(BahrSpacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        BahrLottie(Modifier.width(BahrSize.lottieState), BahrLottie.Props(BahrLottie.Animation.Bell), progress)
                        BahrLottie(Modifier.height(BahrSize.lottieHero), BahrLottie.Props(BahrLottie.Animation.Sailboat), progress)
                    }
                    // M3-M8b: the done moment's check, and the empty states' suitcase and magnifier.
                    Row(horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        BahrLottie(Modifier.width(BahrSize.lottieMoment), BahrLottie.Props(BahrLottie.Animation.SuccessCheck), progress)
                        BahrLottie(Modifier.width(BahrSize.lottieMoment), BahrLottie.Props(BahrLottie.Animation.Suitcase), progress)
                        BahrLottie(Modifier.width(BahrSize.lottieMoment), BahrLottie.Props(BahrLottie.Animation.Magnifier), progress)
                    }
                    Text(
                        text = if (language == AppLanguage.ARABIC) "مفيش إشعارات لسه" else "No notifications yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    private companion object {
        /** A third in: the bell swinging with its rings out, the boat mid-rock, the check's halo out. */
        const val MID_PROGRESS = .3f
    }
}

/**
 * Waits until [count] BahrLotties draw. The file is read and parsed off the main thread, so idle is
 * not enough; a screenshot taken earlier would show the empty box the animation reserves.
 */
internal fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitUntilLottiesDraw(count: Int = 1) {
    waitUntil(timeoutMillis = LOTTIE_LOAD_TIMEOUT_MS) {
        onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().size >= count
    }
    waitForIdle()
}

private const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L
