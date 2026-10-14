package eg.bahr.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.designsystem.components.BahrSignInButton
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.auth_continue_apple
import eg.bahr.core.localization.generated.resources.auth_continue_google
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.settings.data.FakeSettingsRepository
import eg.bahr.feature.settings.data.FakeSettingsRepository.Companion.account
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * M3-M0b, handoff §2: Profile × {ar, en} × {store not answered yet, signed out, signed in (Google with
 * its email; Apple with a private-relay address, "hidden by Apple")}, Edit personal info × {default,
 * the errors after Save, saving, the account not readable}, and the live language switch from the
 * Language sheet. Light only: the handoff designs dark for the bar and the notifications list.
 *
 * Record: `./gradlew :feature:settings:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
class ProfileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val google = SessionState.SignedIn("منة الله حسن", profileComplete = true, providers = setOf("google"))
    private val apple = SessionState.SignedIn("Menna Hassan", profileComplete = true, providers = setOf("apple"))

    // ---------- Profile ----------

    @Test
    fun unknownArabic() =
        profile("profile_loading", AppLanguage.ARABIC, ProfileUiState(language = AppLanguage.ARABIC, appearance = AppAppearance.LIGHT))

    @Test
    fun unknownEnglish() =
        profile("profile_loading", AppLanguage.ENGLISH, ProfileUiState(language = AppLanguage.ENGLISH, appearance = AppAppearance.LIGHT))

    @Test
    fun signedOutArabic() = profile("profile_signed_out", AppLanguage.ARABIC, signedOut(AppLanguage.ARABIC))

    @Test
    fun signedOutEnglish() = profile("profile_signed_out", AppLanguage.ENGLISH, signedOut(AppLanguage.ENGLISH))

    private fun signedOut(language: AppLanguage) =
        ProfileUiState(language = language, appearance = AppAppearance.LIGHT, session = SessionState.SignedOut)

    /** The prototype's pair: Arabic with Google and its email, English with Apple's relay address. */
    @Test
    fun signedInArabic() =
        profile(
            "profile_signed_in",
            AppLanguage.ARABIC,
            ProfileUiState(language = AppLanguage.ARABIC, appearance = AppAppearance.LIGHT, session = google, account = account()),
        )

    @Test
    fun signedInEnglish() =
        profile(
            "profile_signed_in",
            AppLanguage.ENGLISH,
            ProfileUiState(
                language = AppLanguage.ENGLISH,
                appearance = AppAppearance.LIGHT,
                session = apple,
                account = account(name = "Menna Hassan", email = "x7k2p9q4mz@privaterelay.appleid.com", providers = listOf("apple")),
            ),
        )

    @Test
    fun `the rows call back - personal info and delete account`() {
        var edits = 0
        var deletes = 0
        var signOuts = 0
        compose.setContent {
            BahrTheme(locale = BahrLocale.English) {
                ProfileContent(
                    state = ProfileUiState(language = AppLanguage.ENGLISH, session = google, account = account()),
                    appVersion = APP_VERSION,
                    signInButtons = {},
                    actions = ProfileActions(onPersonalInfo = { edits++ }, onDeleteAccount = { deletes++ }, onSignOut = { signOuts++ }),
                )
            }
        }
        compose.onNodeWithTag(ROW_PERSONAL_INFO).performScrollTo().performClick()
        compose.onNodeWithTag(ROW_SIGN_OUT).performScrollTo().performClick()
        compose.onNodeWithTag(ROW_DELETE).performScrollTo().performClick()

        assertEquals(1, edits)
        assertEquals(1, signOuts)
        assertEquals(1, deletes)
    }

    @Test
    fun `picking English in the Language sheet stores it - and the screen redraws in English, left to right`() {
        val repository = FakeSettingsRepository(initial = AppLanguage.ARABIC)
        val vm = ProfileViewModel(repository, AppErrorController())
        compose.setContent {
            // What App does: the stored language decides the composition's language.
            val current = repository.stored.collectAsState().value ?: AppLanguage.default
            Themed(current) {
                ProfileScreen(
                    appVersion = APP_VERSION,
                    signInButtons = { StandInSignInButtons(it) },
                    onEditPersonalInfo = {},
                    deleteAccountSheet = {},
                    viewModel = vm,
                )
            }
        }
        compose.onNodeWithTag(ROW_LANGUAGE).performScrollTo().performClick()
        compose.onNodeWithTag(languageTag(AppLanguage.ARABIC)).assertIsSelected()

        compose.onNodeWithTag(languageTag(AppLanguage.ENGLISH)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(AppLanguage.ENGLISH), repository.writes)
        compose.captureScreenshot("profile_switched_to_en")
    }

    /**
     * M3-M0b review #1: back from a save, "Saved" stays up for the snackbar's time and goes after it, and
     * the notice is cleared only then.
     */
    @Test
    fun `Saved shows after a save and stays until the snackbar times out`() = savedSnackbar(AppLanguage.ENGLISH, "Saved")

    /** Review R2-5: the same in Arabic. */
    @Test
    fun `Saved shows in Arabic too`() = savedSnackbar(AppLanguage.ARABIC, "اتحفظ")

    private fun savedSnackbar(
        language: AppLanguage,
        saved: String,
    ) {
        val repository = FakeSettingsRepository(account = FakeSettingsRepository.SIGNED_IN)
        repository.setSavedNotice(true)
        val vm = ProfileViewModel(repository, AppErrorController())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Themed(language) {
                ProfileScreen(
                    appVersion = APP_VERSION,
                    signInButtons = {},
                    onEditPersonalInfo = {},
                    deleteAccountSheet = {},
                    viewModel = vm,
                )
            }
        }
        compose.mainClock.advanceTimeBy(SNACKBAR_SHOWING_MS)

        compose.onNodeWithText(saved).assertIsDisplayed()
        assertEquals(true, repository.saved.value, "not cleared while it shows")
        compose.captureScreenshot("profile_saved_snackbar_${language.tag}")

        compose.mainClock.advanceTimeBy(SNACKBAR_GONE_MS)
        compose.onNodeWithText(saved).assertDoesNotExist()
        assertEquals(false, repository.saved.value)
    }

    // ---------- Sheets (M3-M0b: BahrBottomSheet, user decision 2026-10-14) ----------

    @Test
    fun appearanceSheetArabic() = sheet("profile_appearance_sheet", AppLanguage.ARABIC, ProfileSheet.Appearance)

    @Test
    fun appearanceSheetEnglish() = sheet("profile_appearance_sheet", AppLanguage.ENGLISH, ProfileSheet.Appearance)

    @Test
    fun languageSheetArabic() = sheet("profile_language_sheet", AppLanguage.ARABIC, ProfileSheet.Language)

    @Test
    fun languageSheetEnglish() = sheet("profile_language_sheet", AppLanguage.ENGLISH, ProfileSheet.Language)

    @Test
    fun aboutSheetArabic() = sheet("profile_about_sheet", AppLanguage.ARABIC, ProfileSheet.About)

    @Test
    fun aboutSheetEnglish() = sheet("profile_about_sheet", AppLanguage.ENGLISH, ProfileSheet.About)

    private fun sheet(
        prefix: String,
        language: AppLanguage,
        sheet: ProfileSheet,
    ) {
        compose.setContent {
            Themed(language) {
                Box(Modifier.fillMaxSize().background(BahrTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.clip(BahrTheme.shapes.sheet).background(MaterialTheme.colorScheme.surface).padding(top = BahrSpacing.xl)) {
                        ProfileSheetContent(sheet, ProfileUiState(language = language, appearance = AppAppearance.LIGHT), APP_VERSION)
                    }
                }
            }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    /** The Appearance row opens the picker; a pick reaches the view model. */
    @Test
    fun `the Appearance row opens the picker - a pick is stored`() {
        val repository = FakeSettingsRepository()
        val vm = ProfileViewModel(repository, AppErrorController())
        compose.setContent {
            Themed(AppLanguage.ENGLISH) {
                ProfileScreen(
                    appVersion = APP_VERSION,
                    signInButtons = {},
                    onEditPersonalInfo = {},
                    deleteAccountSheet = {},
                    viewModel = vm,
                )
            }
        }
        compose.onNodeWithTag(ROW_APPEARANCE).performScrollTo().performClick()
        compose.onNodeWithTag(appearanceTag(AppAppearance.SYSTEM)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(AppAppearance.SYSTEM), repository.appearanceWrites)
    }

    // ---------- Edit personal info ----------

    private val filled =
        EditPersonalInfoUiState(
            loading = false,
            name = "منة الله حسن",
            phone = "01012345678",
            email = "menna.hassan@gmail.com",
            provider = "google",
        )

    @Test
    fun editArabic() = edit("edit_personal_info", AppLanguage.ARABIC, filled)

    @Test
    fun editEnglish() =
        edit(
            "edit_personal_info",
            AppLanguage.ENGLISH,
            filled.copy(name = "Menna Hassan", email = "x7k2p9q4mz@privaterelay.appleid.com", provider = "apple"),
        )

    /** The prototype's error: 10 digits after Save, the border, the counter and the message in error. */
    @Test
    fun editErrorArabic() = edit("edit_personal_info_error", AppLanguage.ARABIC, filled.copy(phone = "0101234567", checked = true))

    @Test
    fun editErrorEnglish() =
        edit("edit_personal_info_error", AppLanguage.ENGLISH, filled.copy(name = "Menna Hassan", phone = "0101234567", checked = true))

    @Test
    fun editSavingArabic() = edit("edit_personal_info_saving", AppLanguage.ARABIC, filled.copy(saving = true))

    @Test
    fun editSavingEnglish() = edit("edit_personal_info_saving", AppLanguage.ENGLISH, filled.copy(name = "Menna Hassan", saving = true))

    @Test
    fun editUnreadableArabic() =
        edit("edit_personal_info_unreadable", AppLanguage.ARABIC, EditPersonalInfoUiState(loading = false, loadError = AppError.Network))

    @Test
    fun editUnreadableEnglish() =
        edit("edit_personal_info_unreadable", AppLanguage.ENGLISH, EditPersonalInfoUiState(loading = false, loadError = AppError.Network))

    // ---------- Plumbing ----------

    private fun profile(
        prefix: String,
        language: AppLanguage,
        state: ProfileUiState,
    ) {
        compose.setContent {
            Themed(language) {
                ProfileContent(
                    state = state,
                    appVersion = APP_VERSION,
                    signInButtons = { StandInSignInButtons(it) },
                    actions = ProfileActions(),
                )
            }
        }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private fun edit(
        prefix: String,
        language: AppLanguage,
        state: EditPersonalInfoUiState,
    ) {
        compose.setContent { Themed(language) { EditPersonalInfoContent(state, EditPersonalInfoActions()) } }
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    @Composable
    private fun Themed(
        language: AppLanguage,
        content: @Composable () -> Unit,
    ) {
        ProvideAppLanguage(language) {
            BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) { content() }
        }
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

    private companion object {
        const val APP_VERSION = "1.4.0"

        /** Well inside Material's short snackbar (4s), and well past it. */
        const val SNACKBAR_SHOWING_MS = 1_000L
        const val SNACKBAR_GONE_MS = 6_000L
    }
}
