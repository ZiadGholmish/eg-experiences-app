package eg.bahr.core.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrMotion
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.ANIMATION_SETTLE_MARGIN_MILLIS
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The countdown's rolling digits (M4-M4): only the digits that change roll, the others stay still;
 * under reduce motion the time changes at once. The time reads as one `mm:ss` whatever is rolling.
 *
 * Each character's slot draws the whole time (clipped to its own column), so while a slot rolls it
 * holds two texts, the time it is leaving and the new one; a still slot holds the new time only.
 * Counting the texts that are not the new time counts the rolling digits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class HoldCountdownMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private var secondsLeft by mutableIntStateOf(0)

    @Before
    fun setUp() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun `14_59 to 14_58 rolls the last digit only`() {
        show(secondsLeft = 14 * 60 + 59, reducedMotion = false)
        secondsLeft = 14 * 60 + 58
        midRoll()
        assertEquals(listOf("14:59"), leaving("14:58"), "only the seconds' last digit rolls")

        settle()
        assertEquals(emptyList(), leaving("14:58"))
        compose.onNodeWithText("14:58", substring = true).assertExists()
    }

    @Test
    fun `13_00 to 12_59 rolls the three digits that change`() {
        show(secondsLeft = 13 * 60, reducedMotion = false)
        secondsLeft = 12 * 60 + 59
        midRoll()
        // Each leaving slot shows its old digit; the rest of its text is clipped away.
        assertEquals(listOf("12:09", "12:50", "13:59"), leaving("12:59").sorted())
    }

    /**
     * The same roll in Arabic (M4-M4 review #7): the Plex Arabic face, an RTL panel, the time still
     * laid out left to right, so the slots and the digits leaving them are the English ones.
     */
    @Test
    fun `in Arabic 13_00 to 12_59 rolls the same three digits`() {
        show(secondsLeft = 13 * 60, reducedMotion = false, arabic = true)
        secondsLeft = 12 * 60 + 59
        midRoll()
        assertEquals(listOf("12:09", "12:50", "13:59"), leaving("12:59").sorted())

        settle()
        assertEquals(emptyList(), leaving("12:59"))
    }

    @Test
    fun `under reduce motion the time changes at once`() {
        show(secondsLeft = 13 * 60, reducedMotion = true)
        secondsLeft = 12 * 60 + 59
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(emptyList(), leaving("12:59"))
        compose.onNodeWithText("12:59", substring = true).assertExists()
    }

    private fun show(
        secondsLeft: Int,
        reducedMotion: Boolean,
        arabic: Boolean = false,
    ) {
        this.secondsLeft = secondsLeft
        compose.setContent {
            ProvideAppLanguage(if (arabic) AppLanguage.ARABIC else AppLanguage.ENGLISH) {
                BahrTheme(locale = if (arabic) BahrLocale.Arabic else BahrLocale.English, reducedMotion = reducedMotion) {
                    HoldCountdown(secondsLeft = this.secondsLeft, label = LABEL, progress = 1f)
                }
            }
        }
        compose.waitForIdle()
    }

    /** A couple of frames into the roll: well before [BahrMotion.Medium] ends. */
    private fun midRoll() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(BahrMotion.Medium.toLong() + ANIMATION_SETTLE_MARGIN_MILLIS)
        compose.waitForIdle()
    }

    /**
     * The slot texts that are not [now]: one per rolling digit. Read from the unmerged tree, where the
     * slots' texts sit under the row that reads as one `mm:ss`.
     */
    private fun leaving(now: String): List<String> =
        compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") }
            .filter { TIME.matches(it) && it != now }

    private companion object {
        const val LABEL = "Seats held for"
        val TIME = Regex("""\d\d:\d\d""")
    }
}
