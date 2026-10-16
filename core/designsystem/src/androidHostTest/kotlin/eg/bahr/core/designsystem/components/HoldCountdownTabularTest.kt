package eg.bahr.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The countdown measures one template of its time's length (every digit a 0) instead of each new
 * time (M3-M11, Compose audit L17). That holds only while every digit is as wide as every other in
 * the countdown's style: the app's type sets `tnum`, and this checks the real fonts honour it, in
 * both languages, for the line's width and every column edge. Native graphics, so text is measured
 * with the real fonts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class HoldCountdownTabularTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every countdown time lays out like its template - English`() = assertTabular(AppLanguage.ENGLISH, BahrLocale.English)

    @Test
    fun `every countdown time lays out like its template - Arabic`() = assertTabular(AppLanguage.ARABIC, BahrLocale.Arabic)

    private fun assertTabular(
        language: AppLanguage,
        locale: BahrLocale,
    ) {
        val layouts = mutableMapOf<String, TextLayoutResult>()
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = locale) {
                    val measurer = rememberTextMeasurer()
                    val style: TextStyle = MaterialTheme.typography.headlineMedium
                    (SAMPLES + SAMPLES.map(::countdownTemplate)).forEach { time ->
                        layouts[time] = measurer.measure(time, style, softWrap = false, maxLines = 1)
                    }
                }
            }
        }
        compose.waitForIdle()

        SAMPLES.forEach { time ->
            val line = layouts.getValue(time)
            val template = layouts.getValue(countdownTemplate(time))
            assertEquals(template.size.width, line.size.width, "$time is not as wide as its template")
            for (at in 1 until time.length) {
                assertEquals(
                    template.getHorizontalPosition(at, usePrimaryDirection = true),
                    line.getHorizontalPosition(at, usePrimaryDirection = true),
                    "$time: column $at moved",
                )
            }
        }
    }

    private companion object {
        /** Every digit in every position, plus the ends of a hold. */
        val SAMPLES =
            listOf(15 * 60, 14 * 60 + 59, 11 * 60 + 11, 9 * 60 + 47, 2 * 60 + 38, 1 * 60 + 6, 0, 59)
                .map(BahrFormat::countdown)
    }
}
