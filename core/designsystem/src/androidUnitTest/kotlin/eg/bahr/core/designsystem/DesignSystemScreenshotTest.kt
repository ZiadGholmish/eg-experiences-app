package eg.bahr.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.BahrPrimaryButton
import eg.bahr.core.designsystem.components.SeatBadge
import eg.bahr.core.designsystem.error.BahrErrorSnackbar
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.generated.resources.booking_confirm
import eg.bahr.core.localization.generated.resources.error_no_seats_available
import eg.bahr.core.testing.captureScreenshot
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import eg.bahr.core.localization.generated.resources.Res as L10n

/**
 * Design-system goldens. Record: `./gradlew recordRoborazziDebug`; verify:
 * `./gradlew verifyRoborazziDebug` (see README.md → Testing).
 *
 * Every capture goes through [ProvideAppLanguage] + [BahrTheme], the same pair the app root uses,
 * so the Arabic shots also prove the real Plex Arabic face and RTL reach the screenshot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class DesignSystemScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var deviceLocale: Locale

    @Before
    fun freezeClock() {
        deviceLocale = Locale.getDefault()
        // Indeterminate spinners animate forever; a paused clock makes the frame deterministic.
        compose.mainClock.autoAdvance = false
    }

    @After
    fun restoreLocale() = Locale.setDefault(deviceLocale)

    @Test
    fun primaryButton() =
        snapEach(perLanguage("primary_button")) {
            val label = stringResource(L10n.string.booking_confirm)
            BahrPrimaryButton(text = label, onClick = {}, modifier = Modifier.fillMaxWidth())
            BahrPrimaryButton(text = label, onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            BahrPrimaryButton(text = label, onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
            BahrPrimaryButton(
                text = label,
                onClick = {},
                leadingIcon = BahrIcons.ArrowForward.outlined(),
                trailing = BahrFormat.money(450, "EGP", BahrTheme.locale.isArabic),
                modifier = Modifier.fillMaxWidth(),
            )
        }

    /** 12 = available, 3 = few, 0 = sold out. Not 1: "1 seats left" is the known plural gap (M1-M1). */
    @Test
    fun seatBadge() =
        snapEach(perLanguage("seat_badge")) {
            SeatBadge(left = 12)
            SeatBadge(left = 3)
            SeatBadge(left = 0)
        }

    /**
     * Every [BahrIcons] entry, outlined then filled. In RTL exactly the directional symbols
     * (arrows, chat, event_upcoming, format_list_bulleted) must mirror; nothing else may.
     * Also catches an icon whose XML only fails at draw time.
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun iconGallery() =
        snapEach(listOf(AppLanguage.ENGLISH to "icons_ltr", AppLanguage.ARABIC to "icons_rtl")) {
            listOf(true, false).forEach { outlined ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
                ) {
                    BahrIcons.entries.forEach { icon ->
                        Icon(
                            imageVector = if (outlined) icon.outlined() else icon.filled(),
                            contentDescription = icon.name,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

    @Test
    fun errorSnackbar() =
        snapEach(perLanguage("error_snackbar")) {
            BahrErrorSnackbar(L10n.string.error_no_seats_available)
        }

    /**
     * One composition per test (a compose rule allows a single `setContent`), re-rendered per
     * language by flipping the language the same way the app's in-place toggle does.
     */
    private fun snapEach(
        shots: List<Pair<AppLanguage, String>>,
        content: @Composable () -> Unit,
    ) {
        var language by mutableStateOf(shots.first().first)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(BahrSpacing.gutter),
                        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
                    ) { content() }
                }
            }
        }
        shots.forEach { (shotLanguage, name) ->
            compose.runOnUiThread { language = shotLanguage }
            // The clock is paused: let the snapshot change apply, then draw exactly one frame.
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.captureScreenshot(name)
        }
    }

    private fun perLanguage(prefix: String) = AppLanguage.entries.map { it to "${prefix}_${it.tag}" }
}
