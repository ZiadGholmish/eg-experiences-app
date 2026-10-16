package eg.bahr.core.designsystem.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * At twice the font size the countdown's time would be wider than its column (M4-M4 review #4); it is
 * shrunk to fit, so it ends before the progress bar starts and no digit is cut off. Native graphics,
 * so text is measured with the real fonts (the legacy mode measures it near zero wide).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class HoldCountdownFontScaleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `at a large font scale the time stays inside its column - whole`() {
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BahrTheme(locale = BahrLocale.English) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)) {
                        HoldCountdown(
                            secondsLeft = 14 * 60 + 58,
                            label = LABEL,
                            progress = 1f,
                            icon = BahrIcons.Timer.filled(),
                            modifier = Modifier.width(PANEL_WIDTH.dp).testTag(PANEL),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val panel = compose.onNodeWithTag(PANEL).getBoundsInRoot()
        val time = compose.onNodeWithText("14:58", substring = true).getBoundsInRoot()
        // The column ends where the bar's spacing begins: panel end - padding - bar - spacing.
        val columnEnd = panel.right - BahrSpacing.lg - BAR_WIDTH.dp - BahrSpacing.md
        assertTrue(time.right <= columnEnd, "time ends at ${time.right}, its column at $columnEnd")
        // And it fits rather than being cut off. Each slot draws the whole line (clipped to its own
        // column), so a slot's text node spans the line as laid out: it must end inside the time.
        val lineEnd =
            compose
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") == "14:58" }
                // Position + size, not boundsInRoot: those are clipped to the time's own bounds.
                .maxOf { node -> with(compose.density) { (node.positionInRoot.x + node.size.width).toDp() } }
        assertTrue(lineEnd <= time.right + SUBPIXEL, "the line ends at $lineEnd, past the time's end ${time.right}")
    }

    private companion object {
        const val LABEL = "Seats held for"
        const val PANEL = "panel"

        /** Narrow enough that the time at 2x is wider than its column (about 92dp here). */
        const val PANEL_WIDTH = 260
        const val LARGE_FONT_SCALE = 2f

        /** HoldCountdown's HOLD_BAR_WIDTH. */
        const val BAR_WIDTH = 70

        /** Slot edges are whole pixels; the time's end may not be. */
        val SUBPIXEL = 1.dp
    }
}
