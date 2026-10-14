package eg.bahr.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.auth.data.ProfilePrompt
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
 * in progress again, no provider configured}. M3-M0b: the "Complete your profile" sheet (prefilled, the
 * errors after Save, saving) and the delete-account sheet (M3-M0b: sheets instead of dialogs) (default,
 * a host's account refused, deleting). Drawn from the stateless content, as the screens draw it.
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

    // ---------- "Complete your profile" (M3-M0b): the modal sheet, prefilled, with errors, saving ----------

    private val prompt = ProfilePrompt(name = "منة الله حسن", phone = "", provider = "google")
    private val prefilled = CompleteProfileUiState(prompt = prompt, name = prompt.name)
    private val badPhone = prefilled.copy(phone = "0101234567", checked = true)
    private val saving = prefilled.copy(phone = "01012345678", saving = true)

    @Test
    fun completeProfileArabic() =
        shoot("complete_profile", AppLanguage.ARABIC) {
            SheetFrame { CompleteProfileContent(prefilled, CompleteProfileActions()) }
        }

    @Test
    fun completeProfileEnglish() =
        shoot("complete_profile", AppLanguage.ENGLISH) {
            SheetFrame {
                CompleteProfileContent(
                    prefilled.copy(name = "Menna Hassan", prompt = prompt.copy(provider = "apple")),
                    CompleteProfileActions(),
                )
            }
        }

    @Test
    fun completeProfileErrorArabic() =
        shoot("complete_profile_error", AppLanguage.ARABIC) {
            SheetFrame { CompleteProfileContent(badPhone.copy(name = "م"), CompleteProfileActions()) }
        }

    @Test
    fun completeProfileErrorEnglish() =
        shoot("complete_profile_error", AppLanguage.ENGLISH) {
            SheetFrame { CompleteProfileContent(badPhone.copy(name = "M"), CompleteProfileActions()) }
        }

    @Test
    fun completeProfileSavingArabic() =
        shoot("complete_profile_saving", AppLanguage.ARABIC) {
            SheetFrame { CompleteProfileContent(saving, CompleteProfileActions()) }
        }

    @Test
    fun completeProfileSavingEnglish() =
        shoot("complete_profile_saving", AppLanguage.ENGLISH) {
            SheetFrame { CompleteProfileContent(saving.copy(name = "Menna Hassan"), CompleteProfileActions()) }
        }

    @Test
    fun `the sheet's Save and Later reach the view model`() {
        var saves = 0
        var laters = 0
        compose.setContent {
            BahrTheme(locale = BahrLocale.English) {
                CompleteProfileContent(prefilled, CompleteProfileActions(onSave = { saves++ }, onLater = { laters++ }))
            }
        }
        compose.onNodeWithTag(PROFILE_SAVE_TAG).performClick()
        compose.onNodeWithText("Later").performClick()
        assertEquals(1, saves)
        assertEquals(1, laters)
    }

    @Test
    fun deleteArabic() = shoot("delete_account", AppLanguage.ARABIC) { SheetFrame { DeleteAccountContent(DeleteAccountUiState(), {}, {}) } }

    @Test
    fun deleteEnglish() =
        shoot("delete_account", AppLanguage.ENGLISH) { SheetFrame { DeleteAccountContent(DeleteAccountUiState(), {}, {}) } }

    @Test
    fun deleteHostRefusedArabic() =
        shoot("delete_account_host", AppLanguage.ARABIC) {
            SheetFrame { DeleteAccountContent(DeleteAccountUiState(error = AppError.Api(ApiErrorCodes.CONFLICT, "x", 409)), {}, {}) }
        }

    @Test
    fun deleteHostRefusedEnglish() =
        shoot("delete_account_host", AppLanguage.ENGLISH) {
            SheetFrame { DeleteAccountContent(DeleteAccountUiState(error = AppError.Api(ApiErrorCodes.CONFLICT, "x", 409)), {}, {}) }
        }

    @Test
    fun deletingArabic() =
        shoot(
            "delete_account_deleting",
            AppLanguage.ARABIC,
        ) { SheetFrame { DeleteAccountContent(DeleteAccountUiState(deleting = true), {}, {}) } }

    @Test
    fun deletingEnglish() =
        shoot("delete_account_deleting", AppLanguage.ENGLISH) {
            SheetFrame { DeleteAccountContent(DeleteAccountUiState(deleting = true), {}, {}) }
        }

    /**
     * Handoff §2: "Keep account" is the default focus, so a stray Enter or a switch press keeps it. Focus
     * is a keyboard (and switch access) matter: in touch mode buttons take none, so the test runs the
     * dialog in keyboard mode.
     */
    @Test
    fun `Keep account has the focus when the dialog opens`() {
        compose.setContent {
            CompositionLocalProvider(LocalInputModeManager provides KeyboardMode) {
                BahrTheme(locale = BahrLocale.English) { DeleteAccountContent(DeleteAccountUiState(), {}, {}) }
            }
        }
        compose.onNodeWithTag(DELETE_CANCEL_TAG).assertIsFocused()
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

    /** What a modal bottom sheet shows over: the dimmed page, the sheet at the bottom with its handle's room. */
    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(BahrTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.clip(BahrTheme.shapes.sheet).background(MaterialTheme.colorScheme.surface).padding(top = BahrSpacing.xl),
            ) { content() }
        }
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

    private object KeyboardMode : InputModeManager {
        override val inputMode: InputMode = InputMode.Keyboard

        override fun requestInputMode(inputMode: InputMode): Boolean = inputMode == InputMode.Keyboard
    }
}
