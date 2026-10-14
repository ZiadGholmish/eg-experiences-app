package eg.bahr.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.auth.model.AuthProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * M3-M0: the sign-in sheet × {ar, en} × {iOS's Apple + Google, Android's Google with a refused sign-in
 * in progress again, no provider configured, the first-sign-in profile with a bad phone}, and the
 * delete-account dialog. Drawn from the stateless content, as the screen draws it.
 *
 * Record: `./gradlew :feature:auth:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class SignInScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val both = listOf(AuthProvider.Apple, AuthProvider.Google)

    @Test
    fun chooseArabic() = shoot("sign_in_choose", AppLanguage.ARABIC) { SignInContent(SignInUiState(), both, SignInActions()) }

    @Test
    fun chooseEnglish() = shoot("sign_in_choose", AppLanguage.ENGLISH) { SignInContent(SignInUiState(), both, SignInActions()) }

    /** Android (Google only): a refused token's message, and Google busy again. */
    private val refused = SignInUiState(busy = AuthProvider.Google, error = AppError.Api(ApiErrorCodes.UNAUTHORIZED, "x", 401))

    @Test
    fun refusedArabic() =
        shoot("sign_in_refused_busy", AppLanguage.ARABIC) {
            SignInContent(refused, listOf(AuthProvider.Google), SignInActions())
        }

    @Test
    fun refusedEnglish() =
        shoot("sign_in_refused_busy", AppLanguage.ENGLISH) {
            SignInContent(refused, listOf(AuthProvider.Google), SignInActions())
        }

    @Test
    fun unavailableArabic() =
        shoot("sign_in_unavailable", AppLanguage.ARABIC) { SignInContent(SignInUiState(), emptyList(), SignInActions()) }

    @Test
    fun unavailableEnglish() =
        shoot("sign_in_unavailable", AppLanguage.ENGLISH) {
            SignInContent(SignInUiState(), emptyList(), SignInActions())
        }

    private val profile = SignInUiState(step = SignInUiState.Step.Profile, name = "سارة علي", phone = "01012")

    @Test
    fun profileArabic() = shoot("sign_in_profile_bad_phone", AppLanguage.ARABIC) { SignInContent(profile, both, SignInActions()) }

    @Test
    fun profileEnglish() = shoot("sign_in_profile_bad_phone", AppLanguage.ENGLISH) { SignInContent(profile, both, SignInActions()) }

    @Test
    fun deleteArabic() =
        shoot("delete_account", AppLanguage.ARABIC) { DialogFrame { DeleteAccountContent(DeleteAccountUiState(), {}, {}) } }

    @Test
    fun deleteEnglish() =
        shoot("delete_account", AppLanguage.ENGLISH) { DialogFrame { DeleteAccountContent(DeleteAccountUiState(), {}, {}) } }

    @Test
    fun deleteHostRefusedArabic() =
        shoot("delete_account_host", AppLanguage.ARABIC) {
            DialogFrame { DeleteAccountContent(DeleteAccountUiState(error = AppError.Api(ApiErrorCodes.CONFLICT, "x", 409)), {}, {}) }
        }

    @Test
    fun deleteHostRefusedEnglish() =
        shoot("delete_account_host", AppLanguage.ENGLISH) {
            DialogFrame { DeleteAccountContent(DeleteAccountUiState(error = AppError.Api(ApiErrorCodes.CONFLICT, "x", 409)), {}, {}) }
        }

    @Test
    fun `a provider tap reaches the view model`() {
        val tapped = mutableListOf<AuthProvider>()
        compose.setContent {
            BahrTheme(locale = BahrLocale.English) {
                SignInContent(SignInUiState(), both, SignInActions(onProvider = { tapped += it }))
            }
        }
        compose.onNodeWithTag(providerTag(AuthProvider.Apple)).performClick()
        compose.onNodeWithTag(providerTag(AuthProvider.Google)).performClick()
        assertEquals(both, tapped)
    }

    /** What a dialog destination shows over: the dimmed page, the card centred with a margin. */
    @Composable
    private fun DialogFrame(content: @Composable () -> Unit) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM)).padding(BahrSpacing.xl),
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    private fun shoot(
        prefix: String,
        language: AppLanguage,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private companion object {
        /** Material's dialog scrim strength. */
        const val SCRIM = 0.32f
    }
}
