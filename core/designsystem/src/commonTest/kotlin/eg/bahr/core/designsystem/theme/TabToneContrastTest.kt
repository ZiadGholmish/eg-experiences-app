package eg.bahr.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The bottom bar's colours (M3-M0b, handoff §1; M4-M8's per-tab tones went with it), and the other
 * pairs the account handoff asks to check ("the badge, error text and the unread tint in dark mode"),
 * in every theme, Dark included. WCAG AA: 4.5:1 for text (the bar's labels are 12sp, the badge 11sp),
 * 3:1 for an icon on its ground.
 */
class TabToneContrastTest {
    private class Pair(
        val what: String,
        val content: (BahrColorTheme) -> Color,
        val ground: (BahrColorTheme) -> Color,
        val min: Float,
    )

    private val pairs =
        listOf(
            Pair("selected icon on the nav indicator", { it.scheme.onPrimaryContainer }, { it.extended.navIndicator }, ICON),
            Pair("selected label on the bar", { it.scheme.onSurface }, { it.extended.navSurface }, TEXT),
            Pair("unselected icon and label on the bar", { it.scheme.onSurfaceVariant }, { it.extended.navSurface }, TEXT),
            Pair("badge count on the badge", { it.extended.onBadge }, { it.extended.badge }, TEXT),
            Pair("Delete account on its errorStrong fill", { it.scheme.onError }, { it.extended.errorStrong }, TEXT),
            Pair("error text on the page", { it.scheme.onErrorContainer }, { it.scheme.surface }, TEXT),
            Pair("a notification's text on the unread tint", { it.scheme.onSurface }, { it.extended.unread }, TEXT),
        )

    @Test
    fun `the bar - the badge - error text and the unread tint read at AA in every theme - dark included`() {
        val failures =
            BahrThemeName.entries.flatMap { name ->
                val theme = bahrColorTheme(name)
                pairs.mapNotNull { pair ->
                    val ratio = contrast(pair.content(theme), pair.ground(theme))
                    if (ratio < pair.min) "$name, ${pair.what}: $ratio (needs ${pair.min})" else null
                }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** The test would pass vacuously if Dark were not among the themes it walks. */
    @Test
    fun `dark is one of the themes checked`() {
        assertTrue(BahrThemeName.Dark in BahrThemeName.entries)
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
        const val TEXT = 4.5f
        const val ICON = 3f
        const val LUMINANCE_OFFSET = 0.05f
    }
}
