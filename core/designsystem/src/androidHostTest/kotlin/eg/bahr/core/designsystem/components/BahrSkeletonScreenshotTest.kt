package eg.bahr.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
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
 * The skeleton blocks (M3-M14) × {ar, en} × {light, dark, HighContrast}, each at the pulse's faintest
 * frame (alpha .5, the frame they start on), which is the one that must stay legible; plus the
 * pulse's brightest frame and the still frame under reduced motion.
 *
 * The clock's own contract (frames only while a placeholder is attached) is BahrShimmerClockTest's.
 *
 * Record: `./gradlew :core:designsystem:recordRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class BahrSkeletonScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun setUp() {
        deviceLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun light() = snapEach("skeleton_blocks", BahrThemeName.LakeBurullus)

    @Test
    fun dark() = snapEach("skeleton_blocks_dark", BahrThemeName.Dark)

    @Test
    fun highContrast() = snapEach("skeleton_blocks_high_contrast", BahrThemeName.HighContrast)

    /** Half a cycle in: every block at its brightest (.9), all in step. */
    @Test
    fun pulsePeak() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Blocks(AppLanguage.ENGLISH, BahrThemeName.LakeBurullus) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(BahrMotion.Shimmer / 2L)
        compose.captureScreenshot("skeleton_blocks_peak_en")
    }

    /** Reduced motion: still, at the pulse's middle. */
    @Test
    fun reducedMotionIsStillAndIdle() {
        compose.setContent { Blocks(AppLanguage.ENGLISH, BahrThemeName.LakeBurullus, reducedMotion = true) }
        compose.waitForIdle()
        compose.captureScreenshot("skeleton_blocks_reduced_motion_en")
    }

    /** For screen readers a skeleton is one "Loading", as the spinner it replaces was. */
    @Test
    fun skeletonSaysLoading() {
        compose.mainClock.autoAdvance = false
        compose.setContent { BahrTheme(locale = BahrLocale.English) { BahrSkeleton { BahrSkeletonLine() } } }
        compose.waitForIdle()
        compose.onNodeWithTag(BAHR_SKELETON_TAG).assertContentDescriptionEquals("Loading")
    }

    private fun snapEach(
        prefix: String,
        theme: BahrThemeName,
    ) {
        compose.mainClock.autoAdvance = false
        var language by mutableStateOf(AppLanguage.ARABIC)
        compose.setContent { Blocks(language, theme) }
        AppLanguage.entries.forEach { shot ->
            compose.runOnUiThread { language = shot }
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.captureScreenshot("${prefix}_${shot.tag}")
        }
    }

    /** A known title drawn as text over a card of placeholders, as a loading list item is. */
    @Composable
    private fun Blocks(
        language: AppLanguage,
        theme: BahrThemeName,
        reducedMotion: Boolean = false,
    ) {
        ProvideAppLanguage(language) {
            BahrTheme(
                locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                theme = theme,
                reducedMotion = reducedMotion,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(BahrSpacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(BahrSpacing.lg),
                ) {
                    Text(
                        text = if (language == AppLanguage.ARABIC) "رحلات مميزة" else "Featured trips",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    BahrSkeleton {
                        BahrSkeletonCard {
                            BahrSkeletonBlock(Modifier.fillMaxWidth().aspectRatio(PHOTO_RATIO))
                            Column(Modifier.padding(BahrSpacing.lg), verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
                                ) {
                                    BahrSkeletonCircle(props = BahrSkeletonCircle.Props(size = BahrSize.avatarSmall))
                                    BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = NAME_WIDTH))
                                }
                                BahrSkeletonLine()
                                BahrSkeletonLine(props = BahrSkeletonLine.Props(widthFraction = LAST_LINE_WIDTH))
                            }
                        }
                        BahrSkeletonBlock(
                            modifier = Modifier.fillMaxWidth().aspectRatio(BANNER_RATIO),
                            props = BahrSkeletonBlock.Props(shape = BahrTheme.shapes.hero),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val PHOTO_RATIO = 2f
        const val BANNER_RATIO = 3f
        const val NAME_WIDTH = .4f
        const val LAST_LINE_WIDTH = .6f
    }
}
