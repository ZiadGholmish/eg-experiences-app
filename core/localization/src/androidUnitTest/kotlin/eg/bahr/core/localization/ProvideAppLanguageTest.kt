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
}
