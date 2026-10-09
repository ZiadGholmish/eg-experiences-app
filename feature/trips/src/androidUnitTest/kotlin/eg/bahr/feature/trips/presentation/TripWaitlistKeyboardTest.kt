package eg.bahr.feature.trips.presentation

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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
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
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.detail
import eg.bahr.feature.trips.data.TripFixtures.saturdays
import eg.bahr.feature.trips.presentation.components.WAITLIST_PHONE_TAG
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * The waiting-list phone field stays on screen and above the sticky bar once the keyboard is up
 * (M2-M3 review #1), modelled on booking's `BookingKeyboardTest`. The keyboard is a real IME inset
 * dispatched to the window, the way the platform delivers it; the field is focused first, as a tap
 * does, and the inset arrives after. Here the field is inside a `LazyColumn` item low on the page,
 * a different container from booking's scrolling column.
 *
 * The asserts measure the field itself (by tag) with unclipped geometry, `positionInRoot` + `size`:
 * `boundsInRoot` is cut to the list's viewport, so it would pass with the field half hidden.
 * A real soft keyboard on a device is still the M2 integration check's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
class TripWaitlistKeyboardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun edgeToEdge() {
        // As MainActivity (enableEdgeToEdge): the window hands the insets to Compose.
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
    }

    @Test
    fun phoneFieldStaysAboveTheBarEnglish() = check(AppLanguage.ENGLISH)

    @Test
    fun phoneFieldStaysAboveTheBarArabic() = check(AppLanguage.ARABIC)

    private fun check(language: AppLanguage) {
        val repo =
            FakeTripRepository(
                tripBySlug = { AppResult.Success(detail()) },
                departuresFor = { AppResult.Success(saturdays()) },
            )
        val vm = TripDetailViewModel("burullus-dawn", repo)
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        TripDetailScreen(slug = "burullus-dawn", onBack = {}, onContinue = { _, _ -> }, viewModel = vm)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            vm.selectDeparture(SOLD_OUT)
            vm.openWaitlist()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(TRIP_PAGE_TAG).performScrollToKey(TripSection.Availability.name)
        compose.waitForIdle()

        // Scrolled so the field just shows above the bar, then tapped: the worst case, where the
        // keyboard's arrival alone pushes it under the bar.
        compose.onNodeWithTag(WAITLIST_PHONE_TAG).performScrollTo().performClick()
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

        val field = compose.onNodeWithTag(WAITLIST_PHONE_TAG).fetchSemanticsNode().unclippedBounds()
        val bar = compose.onNodeWithTag(TRIP_STICKY_BAR_TAG).fetchSemanticsNode().unclippedBounds()
        // Checked first, on its own: with the bar left under the keyboard (no imePadding), the field can
        // still be above the bar and yet hidden by the keyboard.
        assertTrue(
            field.bottom <= rootHeight - imeHeight + 1,
            "the phone field ($field) is above the keyboard (from ${rootHeight - imeHeight})",
        )
        assertTrue(bar.bottom <= rootHeight - imeHeight + 1, "the bar sits on the keyboard: $bar, ime from ${rootHeight - imeHeight}")
        assertTrue(field.bottom <= bar.top, "the phone field ($field) is above the sticky bar ($bar)")
        assertTrue(field.top >= 0f, "the phone field ($field) is on screen")

        compose.captureScreenshot("trip_detail_waitlist_keyboard_${language.tag}")
    }

    /** Where the node really is, clipped by nothing (`boundsInRoot` is cut to the list's viewport). */
    private fun SemanticsNode.unclippedBounds(): Rect = Rect(positionInRoot, size.toSize())

    private companion object {
        const val SOLD_OUT = "dep-3"

        /** A phone keyboard takes about 40 % of the screen's height. */
        const val IME_SHARE = 40
        const val SETTLE_MS = 1_000L
    }
}
