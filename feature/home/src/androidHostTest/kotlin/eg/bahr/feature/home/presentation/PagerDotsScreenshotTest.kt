package eg.bahr.feature.home.presentation

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
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.home.presentation.components.PagerDots
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M4-M14: the carousel's liquid dots, alone and magnified (a 7dp dot is a few pixels in a Home shot),
 * at a fixed scroll position × {ar, en}: on the first banner (offset 0), half-way to the second (the
 * stretched pill), settled on the second, half-way through the wrap from the last banner to the
 * first, and half-way under reduce motion (no stretch: one resting pill on the current page's dot;
 * the time-based cross-fade when it moves on is not in a still). Arabic is mirrored: the first
 * banner's dot is on the right.
 *
 * Record: `./gradlew :feature:home:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w120dp-h56dp-xxxhdpi")
class PagerDotsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun offsetZeroArabic() = shot("pager_dots_offset_0", AppLanguage.ARABIC, page = 0, offset = 0f)

    @Test
    fun offsetZeroEnglish() = shot("pager_dots_offset_0", AppLanguage.ENGLISH, page = 0, offset = 0f)

    @Test
    fun halfWayArabic() = shot("pager_dots_offset_half", AppLanguage.ARABIC, page = 0, offset = HALF)

    @Test
    fun halfWayEnglish() = shot("pager_dots_offset_half", AppLanguage.ENGLISH, page = 0, offset = HALF)

    @Test
    fun settledArabic() = shot("pager_dots_settled", AppLanguage.ARABIC, page = 1, offset = 0f)

    @Test
    fun settledEnglish() = shot("pager_dots_settled", AppLanguage.ENGLISH, page = 1, offset = 0f)

    @Test
    fun wrapHalfWayArabic() = shot("pager_dots_wrap_half", AppLanguage.ARABIC, page = COUNT - 1, offset = HALF)

    @Test
    fun wrapHalfWayEnglish() = shot("pager_dots_wrap_half", AppLanguage.ENGLISH, page = COUNT - 1, offset = HALF)

    @Test
    fun reducedMotionHalfWayArabic() =
        shot("pager_dots_reduced_motion_half", AppLanguage.ARABIC, page = 0, offset = HALF, reducedMotion = true)

    @Test
    fun reducedMotionHalfWayEnglish() =
        shot("pager_dots_reduced_motion_half", AppLanguage.ENGLISH, page = 0, offset = HALF, reducedMotion = true)

    private fun shot(
        prefix: String,
        language: AppLanguage,
        page: Int,
        offset: Float,
        reducedMotion: Boolean = false,
    ) {
        val arabic = language == AppLanguage.ARABIC
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (arabic) BahrLocale.Arabic else BahrLocale.English, reducedMotion = reducedMotion) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        PagerDots(
                            count = COUNT,
                            current = page % COUNT,
                            page = { page },
                            offset = { offset },
                            onSelect = {},
                        )
                    }
                }
            }
        }
        compose.captureScreenshot("${prefix}_${if (arabic) "ar" else "en"}")
    }

    private companion object {
        /** Four banners, like the seed's Home carousel. */
        const val COUNT = 4
        const val HALF = .5f
    }
}
