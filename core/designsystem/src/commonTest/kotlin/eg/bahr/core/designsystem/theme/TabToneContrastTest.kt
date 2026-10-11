package eg.bahr.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import eg.bahr.core.designsystem.components.BahrBadge
import eg.bahr.core.designsystem.components.colorsIn
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * M4-M8: a selected tab's icon and label take its tone's content colour (`BahrBadge.Tone.colors()`,
 * through the same [colorsIn] mapping),
 * on the bar (`surfaceLowest`) around the pill and on the tone's container pill itself. Both must
 * reach 3:1 (WCAG non-text contrast, and the large-ish label) in every theme.
 */
class TabToneContrastTest {
    @Test
    fun `every tab tone's content colour reads at 3 to 1 on the bar and on its pill - in every theme`() {
        val failures =
            BahrThemeName.entries.flatMap { name ->
                val theme = bahrColorTheme(name)
                BahrBadge.Tone.entries.filter { it in TAB_TONES }.flatMap { tone ->
                    val (container, content) = tone.colorsIn(theme.scheme, theme.extended)
                    listOf("bar" to theme.extended.surfaceLowest, "pill" to container).mapNotNull { (where, ground) ->
                        val ratio = contrast(content, ground)
                        if (ratio < MIN_CONTRAST) "$name $tone on $where: $ratio" else null
                    }
                }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** The tabs not selected: muted, but still readable (icon and label in onSurfaceVariant on the bar). */
    @Test
    fun `an unselected tab reads at 3 to 1 on the bar - in every theme`() {
        val failures =
            BahrThemeName.entries.mapNotNull { name ->
                val theme = bahrColorTheme(name)
                val ratio = contrast(theme.scheme.onSurfaceVariant, theme.extended.surfaceLowest)
                if (ratio < MIN_CONTRAST) "$name unselected: $ratio" else null
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + LUMINANCE_OFFSET) / (min(la, lb) + LUMINANCE_OFFSET)
    }

    private companion object {
        /** The tones the bar's tabs use (composeApp's AppTab.tone). */
        val TAB_TONES = setOf(BahrBadge.Tone.Primary, BahrBadge.Tone.Secondary, BahrBadge.Tone.Success, BahrBadge.Tone.Quaternary)

        const val MIN_CONTRAST = 3f
        const val LUMINANCE_OFFSET = 0.05f
    }
}
