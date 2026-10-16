package eg.bahr.feature.booking.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.BookingFixtures.SLUG
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.presentation.components.PHONE_FIELD_TAG
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * The phone field on date + party stays above the sticky bar once the keyboard is up (M2-M1
 * follow-up, M2-M2 review #2). The keyboard is a real IME inset dispatched to the window, the way the
 * platform delivers it; the field is focused first, as a tap does, and the inset arrives after.
 *
 * No app code does this: Compose's own bring-into-view for a focused text field re-runs as the IME
 * inset shrinks the form. M2-M2 shipped a helper for it (`keptAboveKeyboard`); with it stubbed out
 * this test still passed and its goldens were pixel-identical (M2-M2 review R2-1), so it was removed
 * in M2-M4 and this test stays as the guard for the platform behaviour. A real soft keyboard on a
 * device is still the M2 integration check's.
 *
 * The asserts measure the phone field itself (by tag) with unclipped geometry, `positionInRoot` +
 * `size`: the floating label sits on the field's top border, and `boundsInRoot` is clipped by the
 * scroll viewport, so either would pass with the field half hidden.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class BookingKeyboardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun edgeToEdge() {
        // As MainActivity (enableEdgeToEdge): the window hands the insets to Compose.
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
    }

    @Test
    fun phoneFieldStaysAboveTheBarEnglish() = check(AppLanguage.ENGLISH, "Hold seats and pay")

    @Test
    fun phoneFieldStaysAboveTheBarArabic() = check(AppLanguage.ARABIC, "احجز المقاعد وادفع")

    private fun check(
        language: AppLanguage,
        cta: String,
    ) {
        val repo =
            FakeBookingRepository(
                tripBySlug = { AppResult.Success(BookingFixtures.trip()) },
                departuresFor = { AppResult.Success(BookingFixtures.saturdays()) },
            )
        val vm = BookingViewModel(SLUG, "dep-2", repo, FakeActiveHoldStore())
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        BookingScreen(slug = SLUG, departureId = "dep-2", onBack = {}, onHeld = { _, _ -> }, viewModel = vm)
                    }
                }
            }
        }
        compose.waitForIdle()

        // Scrolled so the field sits just above the bar, then tapped: the worst case, where the
        // keyboard's arrival alone pushes it under the bar.
        compose.onNodeWithTag(PHONE_FIELD_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        val rootHeight =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .size.height
        val imeHeight = rootHeight * IME_SHARE / 100
        compose.runOnUiThread {
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeHeight))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()

        val field = compose.onNodeWithTag(PHONE_FIELD_TAG).fetchSemanticsNode().unclippedBounds()
        val button = compose.onNodeWithText(cta).fetchSemanticsNode().unclippedBounds()
        assertTrue(button.bottom <= rootHeight - imeHeight + 1, "the bar sits on the keyboard: $button, ime from ${rootHeight - imeHeight}")
        assertTrue(field.bottom <= button.top, "the phone field ($field) is above the bar's button ($button)")
        assertTrue(field.top >= 0f, "the phone field ($field) is on screen")

        compose.captureScreenshot("booking_keyboard_${language.tag}")
    }

    /** Where the node really is, clipped by nothing (`boundsInRoot` is cut to the scroll viewport). */
    private fun SemanticsNode.unclippedBounds(): Rect = Rect(positionInRoot, size.toSize())

    private companion object {
        /** A phone keyboard takes about 40 % of the screen's height. */
        const val IME_SHARE = 40
        const val SETTLE_MS = 1_000L
    }
}
