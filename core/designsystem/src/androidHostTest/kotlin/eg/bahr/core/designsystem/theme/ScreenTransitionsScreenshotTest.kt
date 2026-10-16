package eg.bahr.core.designsystem.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.generated.resources.tab_home
import eg.bahr.core.localization.generated.resources.tab_profile
import eg.bahr.core.testing.captureScreenshot
import org.jetbrains.compose.resources.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import eg.bahr.core.localization.generated.resources.Res as L10n

/**
 * The screen transitions (M3-M12), caught mid-way, the way a tab's NavHost runs them: two pages that
 * paint nothing themselves, each in a [BahrDestination], so a see-through page would show in the shot.
 *
 * Per platform style (both drawn here on the JVM) and language: the opened page comes from the end
 * (from the right in English, from the left in Arabic), the page underneath shifts toward the start (on
 * iOS 30% and dimmed), and Back mirrors it. A back swipe from the right edge in English moves the page
 * left, with the finger. Under reduce motion the new page is there at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class ScreenTransitionsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun freezeClock() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun iosPushEnglish() = shot("transition_ios_push_en", ScreenMotionStyle.Ios, AppLanguage.ENGLISH, Move.Push)

    @Test
    fun iosPushArabic() = shot("transition_ios_push_ar", ScreenMotionStyle.Ios, AppLanguage.ARABIC, Move.Push)

    @Test
    fun iosPopEnglish() = shot("transition_ios_pop_en", ScreenMotionStyle.Ios, AppLanguage.ENGLISH, Move.Pop)

    @Test
    fun iosPopArabic() = shot("transition_ios_pop_ar", ScreenMotionStyle.Ios, AppLanguage.ARABIC, Move.Pop)

    /** The swipe from the "wrong" edge for English still drags the page with the finger, to the left. */
    @Test
    fun iosSwipeFromRightEnglish() =
        shot("transition_ios_swipe_right_edge_en", ScreenMotionStyle.Ios, AppLanguage.ENGLISH, Move.Pop, BahrBackSwipe.FromRightEdge)

    @Test
    fun androidPushEnglish() = shot("transition_android_push_en", ScreenMotionStyle.Android, AppLanguage.ENGLISH, Move.Push)

    @Test
    fun androidPushArabic() = shot("transition_android_push_ar", ScreenMotionStyle.Android, AppLanguage.ARABIC, Move.Push)

    @Test
    fun androidPopEnglish() = shot("transition_android_pop_en", ScreenMotionStyle.Android, AppLanguage.ENGLISH, Move.Pop)

    @Test
    fun androidPopArabic() = shot("transition_android_pop_ar", ScreenMotionStyle.Android, AppLanguage.ARABIC, Move.Pop)

    @Test
    fun reducedMotion() = shot("transition_reduced_motion_en", ScreenMotionStyle.Ios, AppLanguage.ENGLISH, Move.Push, reducedMotion = true)

    private enum class Move { Push, Pop }

    private fun shot(
        name: String,
        style: ScreenMotionStyle,
        language: AppLanguage,
        move: Move,
        swipe: BahrBackSwipe = BahrBackSwipe.None,
        reducedMotion: Boolean = false,
    ) {
        // Push: page 0 → page 1 on top. Pop: page 1 → page 0 back from under it.
        var page by mutableStateOf(if (move == Move.Push) 0 else 1)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language.isRtl) BahrLocale.Arabic else BahrLocale.English, reducedMotion = reducedMotion) {
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            if (targetState == initialState) {
                                // The first page's own spec, as the NavHost gives its start: level, no motion.
                                ContentTransform(EnterTransition.None, ExitTransition.None)
                            } else if (targetState > initialState) {
                                ContentTransform(pushEnter(reducedMotion, style), pushExit(reducedMotion, style), targetContentZIndex = 1f)
                            } else {
                                ContentTransform(
                                    popEnter(reducedMotion, swipe, style),
                                    popExit(reducedMotion, swipe, style),
                                    // As the NavHost does: the page coming back is drawn under the one leaving.
                                    targetContentZIndex = -1f,
                                )
                            }
                        },
                        label = "screens",
                    ) { shown ->
                        BahrDestination(this, style) { Page(first = shown == 0) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { page = if (move == Move.Push) 1 else 0 }
        compose.waitForIdle()
        val duration = if (style == ScreenMotionStyle.Ios) BahrMotion.ScreenIos else BahrMotion.ScreenAndroid
        val point = if (style == ScreenMotionStyle.Android && move == Move.Pop) ANDROID_POP_POINT else MIDWAY
        compose.mainClock.advanceTimeBy((duration * point).toLong())
        compose.captureScreenshot(name)
    }

    /** A page that paints no background of its own: a title and a bar at its start edge. */
    @Composable
    private fun Page(first: Boolean) {
        Column(Modifier.padding(BahrSpacing.gutter), verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            Text(
                stringResource(if (first) L10n.string.tab_home else L10n.string.tab_profile),
                style = MaterialTheme.typography.headlineMedium,
            )
            val bar = if (first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            Box(
                Modifier
                    .width(BAR_WIDTH)
                    .height(BAR_HEIGHT)
                    .clip(BahrTheme.shapes.medium)
                    .background(bar),
            )
            Text(stringResource(if (first) L10n.string.tab_home else L10n.string.tab_profile), Modifier.fillMaxWidth())
        }
    }

    private companion object {
        /** Mid-way, where both pages show and the slide, dim and fade are all part done. */
        const val MIDWAY = .4f

        /**
         * An Android pop's leaving page fades out over [BahrMotion.Short] (150 ms), sooner than the slide:
         * at [MIDWAY] (180 ms) it has gone, and the shot could not catch it see-through or leaving the wrong
         * way (M3-M12 review #1). A quarter in (112 ms) it is still part drawn.
         */
        const val ANDROID_POP_POINT = .25f
        val BAR_WIDTH = 160.dp
        val BAR_HEIGHT = 120.dp
    }
}
