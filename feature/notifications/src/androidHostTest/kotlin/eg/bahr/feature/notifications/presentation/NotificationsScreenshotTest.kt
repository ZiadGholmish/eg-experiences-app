package eg.bahr.feature.notifications.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.BAHR_LOTTIE_READY_TAG
import eg.bahr.core.designsystem.components.BahrSignInButton
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.auth_continue_apple
import eg.bahr.core.localization.generated.resources.auth_continue_google
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.captureScreenshot
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The notifications page (M3-M0b, handoff §3's states; pushed from Home's bell with a Back since M3-M6a) × {ar, en} × {light, dark} × {signed in: empty, signed
 * out: the sign-in buttons, the stored sign-in not read yet}. The empty state's bell (BahrLottie, M3-M8) is
 * shot at rest: reduced motion shows the frame a played-once animation stops on, so the shot is fixed.
 *
 * Record: `./gradlew :feature:notifications:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class NotificationsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val signedIn = SessionState.SignedIn("Sara", profileComplete = true, providers = setOf("google"))

    @Test
    fun emptyArabic() = shoot("alerts_empty", AppLanguage.ARABIC, signedIn)

    @Test
    fun emptyEnglish() = shoot("alerts_empty", AppLanguage.ENGLISH, signedIn)

    @Test
    fun emptyDarkArabic() = shoot("alerts_empty_dark", AppLanguage.ARABIC, signedIn, BahrThemeName.Dark)

    @Test
    fun emptyDarkEnglish() = shoot("alerts_empty_dark", AppLanguage.ENGLISH, signedIn, BahrThemeName.Dark)

    @Test
    fun signedOutArabic() = shoot("alerts_signed_out", AppLanguage.ARABIC, SessionState.SignedOut)

    @Test
    fun signedOutEnglish() = shoot("alerts_signed_out", AppLanguage.ENGLISH, SessionState.SignedOut)

    @Test
    fun signedOutDarkArabic() = shoot("alerts_signed_out_dark", AppLanguage.ARABIC, SessionState.SignedOut, BahrThemeName.Dark)

    @Test
    fun signedOutDarkEnglish() = shoot("alerts_signed_out_dark", AppLanguage.ENGLISH, SessionState.SignedOut, BahrThemeName.Dark)

    /** The stored sign-in not read yet: the title only, so neither state flashes. */
    @Test
    fun unknownArabic() = shoot("alerts_loading", AppLanguage.ARABIC, SessionState.Unknown)

    @Test
    fun unknownEnglish() = shoot("alerts_loading", AppLanguage.ENGLISH, SessionState.Unknown)

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        session: SessionState,
        theme: BahrThemeName = BahrThemeName.LakeBurullus,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(
                    locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English,
                    theme = theme,
                    reducedMotion = true,
                ) {
                    NotificationsContent(NotificationsUiState(session), onBack = {}, signInButtons = { StandInSignInButtons(it) })
                }
            }
        }
        // The bell's file is read off the main thread: idle alone would shoot its empty box.
        if (session is SessionState.SignedIn) {
            compose.waitUntil(
                LOTTIE_LOAD_TIMEOUT_MS,
            ) { compose.onAllNodesWithTag(BAHR_LOTTIE_READY_TAG).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    /** What feature:auth draws in the slot (on iOS: Apple, then Google), from the design system's buttons. */
    @Composable
    private fun StandInSignInButtons(modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
            BahrSignInButton(
                props = BahrSignInButton.Props(BahrSignInButton.Provider.Apple, stringResource(Res.string.auth_continue_apple)),
            )
            BahrSignInButton(
                props = BahrSignInButton.Props(BahrSignInButton.Provider.Google, stringResource(Res.string.auth_continue_google)),
            )
        }
    }
}

private const val LOTTIE_LOAD_TIMEOUT_MS = 10_000L
