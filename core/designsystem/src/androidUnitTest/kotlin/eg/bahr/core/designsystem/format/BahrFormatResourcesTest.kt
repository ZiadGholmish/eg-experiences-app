package eg.bahr.core.designsystem.format

import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.departure_seats_left
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
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
 * The date words and the seat plurals as the app resolves them: from `core:localization` in the
 * composition's language (not hard-coded EN/AR, M0-M2 review #3), with Western digits, and with
 * Arabic's 1 / 2 / 3–10 / 11+ forms (review R2-2). The device is English on purpose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class BahrFormatResourcesTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun saveLocale() {
        deviceLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    private val saturday = LocalDate(2026, 10, 17)

    private fun resolve(language: AppLanguage): List<String> {
        val out = mutableListOf<String>()
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    out.clear()
                    out += BahrFormat.date(saturday)
                    out += BahrFormat.dayMonthYear(LocalDate(2026, 9, 21))
                    listOf(1, 2, 6, 11).forEach { out += pluralStringResource(Res.plurals.departure_seats_left, it, it) }
                }
            }
        }
        compose.waitForIdle()
        return out.toList()
    }

    @Test
    fun english() {
        assertEquals(
            listOf("Sat 17 Oct", "21 Sep 2026", "1 seat left", "2 seats left", "6 seats left", "11 seats left"),
            resolve(AppLanguage.ENGLISH),
        )
    }

    @Test
    fun arabic() {
        assertEquals(
            listOf("السبت 17 أكتوبر", "21 سبتمبر 2026", "باقي مقعد واحد", "باقي مقعدان", "باقي 6 مقاعد", "باقي 11 مقعدًا"),
            resolve(AppLanguage.ARABIC),
        )
    }
}
