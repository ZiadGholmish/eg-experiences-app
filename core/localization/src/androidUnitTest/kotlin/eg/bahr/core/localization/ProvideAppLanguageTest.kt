package eg.bahr.core.localization

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.trips_title
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

/**
 * The first-launch bug from M0-M3: the app language decided the layout direction, but strings
 * followed the device. Here the "device" is English (`qualifiers = "en"`), and the app language
 * must win for both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ProvideAppLanguageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun rememberDeviceLocale() {
        deviceLocale = Locale.getDefault()
    }

    /** The fix sets the JVM default locale; don't leak it into later tests in this JVM. */
    @After
    fun restoreDeviceLocale() {
        Locale.setDefault(deviceLocale)
    }

    @Test
    fun `Arabic app language on an English device gives Arabic strings and RTL`() {
        var direction: LayoutDirection? = null
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ARABIC) {
                direction = LocalLayoutDirection.current
                BasicText(stringResource(Res.string.trips_title))
            }
        }

        compose.onNodeWithText("الرحلات").assertExists()
        assertEquals(LayoutDirection.Rtl, direction)
    }

    @Test
    fun `English app language gives English strings and LTR`() {
        var direction: LayoutDirection? = null
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                direction = LocalLayoutDirection.current
                BasicText(stringResource(Res.string.trips_title))
            }
        }

        compose.onNodeWithText("Trips").assertExists()
        assertEquals(LayoutDirection.Ltr, direction)
    }

    /**
     * The process default follows the app language, so platform formatting would follow it too:
     * plain `ar` gives Arabic-Indic digits (١٢٣٤) from `String.format`. The default is
     * `ar-u-nu-latn`, a backstop for code that bypasses BahrFormat.
     * (This runs on the JVM's CLDR data under Robolectric, not on Android's ICU.)
     */
    @Test
    fun `Arabic app language keeps Western digits in platform number formatting`() {
        // Control: without the backstop this JVM really does print Arabic-Indic digits, so the
        // assertion below can fail.
        assertEquals("١٢٣٤", String.format(Locale.forLanguageTag("ar"), "%d", 1234))

        compose.setContent {
            ProvideAppLanguage(AppLanguage.ARABIC) {
                BasicText(stringResource(Res.string.trips_title))
            }
        }

        compose.onNodeWithText("الرحلات").assertExists()
        assertEquals("ar", Locale.getDefault().language)
        assertEquals("latn", Locale.getDefault().getUnicodeLocaleType("nu"))
        assertEquals("1234 12.5", String.format("%d %.1f", 1234, 12.5))
    }

    @Test
    fun `English app language keeps Western digits in platform number formatting`() {
        compose.setContent {
            ProvideAppLanguage(AppLanguage.ENGLISH) {
                BasicText(stringResource(Res.string.trips_title))
            }
        }

        compose.onNodeWithText("Trips").assertExists()
        assertEquals("en", Locale.getDefault().language)
        assertEquals("1234 12.5", String.format("%d %.1f", 1234, 12.5))
    }
}
