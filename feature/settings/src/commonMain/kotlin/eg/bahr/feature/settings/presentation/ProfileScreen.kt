package eg.bahr.feature.settings.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.theme.AppAppearance
import eg.bahr.core.designsystem.components.BahrBottomSheet
import eg.bahr.core.designsystem.components.BahrBottomSheetContent
import eg.bahr.core.designsystem.components.BahrProviderMark
import eg.bahr.core.designsystem.components.BahrSignInButton
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.lifecycle.RefreshesOnReturn
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.appearance_dark
import eg.bahr.core.localization.generated.resources.appearance_light
import eg.bahr.core.localization.generated.resources.appearance_system
import eg.bahr.core.localization.generated.resources.profile_about
import eg.bahr.core.localization.generated.resources.profile_appearance
import eg.bahr.core.localization.generated.resources.profile_can_browse
import eg.bahr.core.localization.generated.resources.profile_can_hold
import eg.bahr.core.localization.generated.resources.profile_can_waitlist
import eg.bahr.core.localization.generated.resources.profile_name_phone
import eg.bahr.core.localization.generated.resources.profile_personal_info
import eg.bahr.core.localization.generated.resources.profile_provider_email
import eg.bahr.core.localization.generated.resources.profile_provider_hidden
import eg.bahr.core.localization.generated.resources.profile_saved
import eg.bahr.core.localization.generated.resources.profile_title
import eg.bahr.core.localization.generated.resources.profile_welcome_body
import eg.bahr.core.localization.generated.resources.profile_welcome_title
import eg.bahr.core.localization.generated.resources.profile_without_account
import eg.bahr.core.localization.generated.resources.provider_apple
import eg.bahr.core.localization.generated.resources.provider_google
import eg.bahr.core.localization.generated.resources.settings_about_line
import eg.bahr.core.localization.generated.resources.settings_delete_account
import eg.bahr.core.localization.generated.resources.settings_language
import eg.bahr.core.localization.generated.resources.settings_language_arabic
import eg.bahr.core.localization.generated.resources.settings_language_english
import eg.bahr.core.localization.generated.resources.settings_sign_out
import eg.bahr.core.localization.generated.resources.settings_version
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.settings.presentation.components.ProfileRow
import eg.bahr.feature.settings.presentation.components.ProfileRowGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Profile (M3-M0b, handoff §2), the Profile tab's root; it replaces M4-M8's Settings. Language, About and
 * the version stay reachable signed out.
 *
 * - Signed out: the welcome card with the sign-in buttons ([signInButtons], feature:auth's, wired in
 *   :composeApp; no legal line until terms and privacy pages exist), "Without an account you can" and its three lines, then Language and About.
 * - Signed in: the initials avatar, the name and the provider line (its email, or "hidden by Apple" for
 *   a private-relay address), then three groups: Personal info · Language; About; Sign out · Delete
 *   account. The handoff's Notifications prefs, My reviews and Help & contact rows are left out until
 *   they have something to open (M3-B10, M3-B7 + M3-M4, a support channel): see the step report.
 *
 * Language, Appearance and About open bottom sheets (see [ProfileSheetHost]); Delete account opens
 * feature:auth's sheet, placed through [deleteAccountSheet]. [scrollToTop] fires on a re-tap of the tab.
 */
@Composable
internal fun ProfileScreen(
    appVersion: String,
    signInButtons: @Composable (Modifier) -> Unit,
    onEditPersonalInfo: () -> Unit,
    deleteAccountSheet: @Composable (onDismiss: () -> Unit) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    ScrollsToTop(scroll, scrollToTop)
    RefreshesOnReturn(viewModel::refresh)

    var sheet by rememberSaveable { mutableStateOf<ProfileSheet?>(null) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(Res.string.profile_saved)
    // The flag is cleared only once the snackbar has gone (M3-M0b review #1): clearing it first changed
    // this effect's key, which cancelled it, and a cancelled `showSnackbar` takes its snackbar away.
    LaunchedEffect(state.showSaved) {
        if (state.showSaved) {
            try {
                snackbar.showSnackbar(saved)
            } finally {
                viewModel.savedShown()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ProfileContent(
            state = state,
            appVersion = appVersion,
            signInButtons = signInButtons,
            actions =
                ProfileActions(
                    onPersonalInfo = onEditPersonalInfo,
                    onLanguage = { sheet = ProfileSheet.Language },
                    onAppearance = { sheet = ProfileSheet.Appearance },
                    onAbout = { sheet = ProfileSheet.About },
                    onSignOut = viewModel::signOut,
                    onDeleteAccount = { deleteOpen = true },
                ),
            scroll = scroll,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(BahrSpacing.gutter)) { data ->
            Snackbar(
                shape = BahrTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    sheet?.let { open ->
        ProfileSheetHost(
            sheet = open,
            state = state,
            appVersion = appVersion,
            actions =
                ProfileSheetActions(
                    onLanguage = viewModel::selectLanguage,
                    onAppearance = viewModel::selectAppearance,
                    onDismiss = { sheet = null },
                ),
        )
    }
    // Delete account is feature:auth's sheet, placed here while the row has it open.
    if (deleteOpen) deleteAccountSheet { deleteOpen = false }
}

internal enum class ProfileSheet { Language, Appearance, About }

internal class ProfileActions(
    val onPersonalInfo: () -> Unit = {},
    val onLanguage: () -> Unit = {},
    val onAppearance: () -> Unit = {},
    val onAbout: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
)

/** Stateless, for the screenshot tests. The handoff's page is `surface`, its groups `surfaceLow`. */
@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    appVersion: String,
    signInButtons: @Composable (Modifier) -> Unit,
    actions: ProfileActions,
    scroll: ScrollState = rememberScrollState(),
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                // Below the status bar and clipped there, as Home is (M4-M1a; M3-M11, Compose audit M4):
                // inside the scroll, the inset scrolled away and the rows passed under the clock.
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scroll)
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Text(
            text = stringResource(Res.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }.padding(top = BahrSpacing.sm),
        )
        val language = languageRow(state.language, actions.onLanguage)
        val appearance = appearanceRow(state.appearance, actions.onAppearance)
        val about =
            ProfileRow(
                BahrIcons.Info,
                stringResource(Res.string.profile_about),
                ROW_ABOUT,
                actions.onAbout,
                value = appVersion,
                valueLtr = true,
            )
        when (val session = state.session) {
            // Nothing until the store has answered, so a signed-in reader never sees the sign-in card flash.
            SessionState.Unknown -> Unit
            SessionState.SignedOut -> {
                WelcomeCard(signInButtons)
                WithoutAnAccount()
                ProfileRowGroup(listOf(language, appearance, about))
            }
            is SessionState.SignedIn -> {
                AccountHeader(state, session)
                ProfileRowGroup(listOf(personalInfoRow(state, session, actions.onPersonalInfo), language, appearance))
                // Notifications prefs (M3-B10) and My reviews (M3-B7 + M3-M4), then Help & contact, join
                // About here once they have something to open.
                ProfileRowGroup(listOf(about))
                ProfileRowGroup(
                    listOf(
                        ProfileRow(
                            BahrIcons.Logout,
                            stringResource(Res.string.settings_sign_out),
                            ROW_SIGN_OUT,
                            actions.onSignOut,
                            opensPage = false,
                            enabled = !state.signingOut,
                        ),
                        ProfileRow(
                            BahrIcons.Delete,
                            stringResource(Res.string.settings_delete_account),
                            ROW_DELETE,
                            actions.onDeleteAccount,
                            opensPage = false,
                            danger = true,
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun languageRow(
    language: AppLanguage?,
    onClick: () -> Unit,
) = ProfileRow(
    icon = BahrIcons.Translate,
    label = stringResource(Res.string.settings_language),
    tag = ROW_LANGUAGE,
    onClick = onClick,
    value = language?.let { stringResource(it.nameRes()) },
)

/** Appearance (M3-M0b, user decision 2026-10-14): Light (the default), Dark or Same as phone. */
@Composable
private fun appearanceRow(
    appearance: AppAppearance?,
    onClick: () -> Unit,
) = ProfileRow(
    icon = BahrIcons.Palette,
    label = stringResource(Res.string.profile_appearance),
    tag = ROW_APPEARANCE,
    onClick = onClick,
    value = appearance?.let { stringResource(it.nameRes()) },
)

/** "Personal info" with "name · phone" under it, the phone left to right. */
@Composable
private fun personalInfoRow(
    state: ProfileUiState,
    session: SessionState.SignedIn,
    onClick: () -> Unit,
): ProfileRow {
    val name = state.account?.name ?: session.accountName
    val phone = state.account?.phone
    val sub =
        when {
            name != null && phone != null -> stringResource(Res.string.profile_name_phone, name, BahrFormat.ltr(phone))
            else -> name ?: phone?.let(BahrFormat::ltr)
        }
    return ProfileRow(BahrIcons.Badge, stringResource(Res.string.profile_personal_info), ROW_PERSONAL_INFO, onClick, sub = sub)
}

/** Signed out: welcome, why sign in and the provider buttons, on `surfaceLow`. */
@Composable
private fun WelcomeCard(signInButtons: @Composable (Modifier) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BahrTheme.shapes.card)
                .background(BahrTheme.colors.surfaceLow)
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Text(
            text = stringResource(Res.string.profile_welcome_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.profile_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = BahrTheme.colors.onSurfaceSecondary,
        )
        // The handoff's legal line ("you agree to the Terms and Privacy Policy") waits for those pages to
        // exist (user decision 2026-10-14).
        signInButtons(Modifier.fillMaxWidth())
    }
}

/** "Without an account you can": three lines, each behind a `successContainer` disc. */
@Composable
private fun WithoutAnAccount() {
    Column(Modifier.padding(horizontal = BahrSpacing.xs), verticalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        Text(
            text = stringResource(Res.string.profile_without_account),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CAN_WITHOUT_ACCOUNT.forEach { (icon, line) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(BahrSize.listDisc).clip(BahrTheme.shapes.full).background(BahrTheme.colors.successContainer),
                ) {
                    Icon(
                        icon.outlined(),
                        contentDescription = null,
                        tint = BahrTheme.colors.onSuccessContainer,
                        modifier = Modifier.size(BahrSize.iconSmall),
                    )
                }
                Text(text = stringResource(line), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Signed in: the initials avatar, the name, and how the account signs in. */
@Composable
private fun ColumnScope.AccountHeader(
    state: ProfileUiState,
    session: SessionState.SignedIn,
) {
    val name = state.account?.name ?: session.accountName
    val provider = state.account?.providers?.firstOrNull() ?: session.providers.firstOrNull()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = BahrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(BahrSize.avatarProfile)
                    .clip(BahrTheme.shapes.full)
                    .background(BahrTheme.colors.quaternaryContainer)
                    // The name next to it says who it is.
                    .clearAndSetSemantics {},
        ) {
            val initials = name?.let(::initials).orEmpty()
            if (initials.isEmpty()) {
                Icon(BahrIcons.Person.filled(), contentDescription = null, tint = BahrTheme.colors.onQuaternaryContainer)
            } else {
                Text(text = initials, style = MaterialTheme.typography.titleLarge, color = BahrTheme.colors.onQuaternaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs)) {
            name?.let {
                Text(text = it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            }
            provider?.let { ProviderLine(it, state.account?.email) }
        }
    }
}

@Composable
private fun ProviderLine(
    provider: String,
    email: String?,
) {
    val mark = if (provider == APPLE_PROVIDER) BahrSignInButton.Provider.Apple else BahrSignInButton.Provider.Google
    val providerName = stringResource(if (provider == APPLE_PROVIDER) Res.string.provider_apple else Res.string.provider_google)
    val line =
        when {
            email == null -> providerName
            isAppleRelay(email) -> stringResource(Res.string.profile_provider_hidden)
            else -> stringResource(Res.string.profile_provider_email, providerName, BahrFormat.ltr(email))
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm)) {
        BahrProviderMark(mark)
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Profile's sheets (undesigned; [BahrBottomSheet], user decision 2026-10-14: sheets, not dialogs):
 * Language (the M4-M8 segmented choice), Appearance (Light · Dark · Same as phone) and About (the about
 * line and version; terms and privacy have no pages yet). Delete account is feature:auth's own sheet.
 */
@Composable
private fun ProfileSheetHost(
    sheet: ProfileSheet,
    state: ProfileUiState,
    appVersion: String,
    actions: ProfileSheetActions,
) {
    BahrBottomSheet(props = BahrBottomSheet.Props(title = sheetTitle(sheet), onDismiss = actions.onDismiss)) {
        ProfileSheetBody(sheet, state, appVersion, actions)
    }
}

internal class ProfileSheetActions(
    val onLanguage: (AppLanguage) -> Unit = {},
    val onAppearance: (AppAppearance) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** A sheet's inside, without the sheet's window: for the screenshot tests. */
@Composable
internal fun ProfileSheetContent(
    sheet: ProfileSheet,
    state: ProfileUiState,
    appVersion: String,
    actions: ProfileSheetActions = ProfileSheetActions(),
) {
    BahrBottomSheetContent(BahrBottomSheet.Props(title = sheetTitle(sheet), onDismiss = actions.onDismiss)) {
        ProfileSheetBody(sheet, state, appVersion, actions)
    }
}

@Composable
private fun sheetTitle(sheet: ProfileSheet): String =
    stringResource(
        when (sheet) {
            ProfileSheet.Language -> Res.string.settings_language
            ProfileSheet.Appearance -> Res.string.profile_appearance
            ProfileSheet.About -> Res.string.profile_about
        },
    )

@Composable
private fun ProfileSheetBody(
    sheet: ProfileSheet,
    state: ProfileUiState,
    appVersion: String,
    actions: ProfileSheetActions,
) {
    when (sheet) {
        ProfileSheet.Language -> LanguageChoice(state.language, actions.onLanguage)
        ProfileSheet.Appearance -> AppearanceChoice(state.appearance, actions.onAppearance)
        ProfileSheet.About -> AboutBody(appVersion)
    }
}

/** The two languages side by side, each named in its own language; a pick re-themes the whole app. */
@Composable
private fun LanguageChoice(
    selected: AppLanguage?,
    onSelect: (AppLanguage) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        LANGUAGES.forEachIndexed { index, language ->
            SegmentedButton(
                selected = selected == language,
                onClick = { onSelect(language) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = LANGUAGES.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.heightIn(min = BahrSize.touchTarget).testTag(languageTag(language)),
            ) {
                Text(text = stringResource(language.nameRes()), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Light · Dark · Same as phone, as radio rows (user decision 2026-10-14: Light is the default, dark is
 * opt-in). A pick re-themes every screen at once, the sheet included.
 */
@Composable
private fun AppearanceChoice(
    selected: AppAppearance?,
    onSelect: (AppAppearance) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        APPEARANCES.forEach { appearance ->
            val isSelected = selected == appearance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BahrSpacing.md),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = BahrSize.touchTarget)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(appearance) })
                        .testTag(appearanceTag(appearance)),
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = stringResource(appearance.nameRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AboutBody(appVersion: String) {
    Text(
        text = stringResource(Res.string.settings_about_line),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    // A build name (0.1-local) reads left to right inside Arabic too.
    Text(
        text = stringResource(Res.string.settings_version, BahrFormat.ltr(appVersion)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Back to the top on a re-tap of the tab; at once when the system asks for less motion. */
@Composable
private fun ScrollsToTop(
    scroll: ScrollState,
    requests: Flow<Unit>,
) {
    val reduced = BahrTheme.reducedMotion
    LaunchedEffect(requests, reduced) {
        requests.collect { if (reduced) scroll.scrollTo(0) else scroll.animateScrollTo(0) }
    }
}

/**
 * The avatar's letters: an Arabic name's first letter (Arabic has no capitals and its letters join, so
 * one reads best), otherwise the first letters of the first two words ("MH").
 */
internal fun initials(name: String): String {
    val words = name.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    val first = words.firstOrNull()?.first() ?: return ""
    return if (first in ARABIC_BLOCK) first.toString() else words.take(LATIN_INITIALS).joinToString("") { it.first().uppercase() }
}

/** Apple's private relay ("Hide My Email"), which the handoff shows as "Hidden by Apple". */
internal fun isAppleRelay(email: String): Boolean = email.endsWith(APPLE_RELAY_DOMAIN, ignoreCase = true)

private fun AppLanguage.nameRes(): StringResource =
    when (this) {
        AppLanguage.ARABIC -> Res.string.settings_language_arabic
        AppLanguage.ENGLISH -> Res.string.settings_language_english
    }

private fun AppAppearance.nameRes(): StringResource =
    when (this) {
        AppAppearance.LIGHT -> Res.string.appearance_light
        AppAppearance.DARK -> Res.string.appearance_dark
        AppAppearance.SYSTEM -> Res.string.appearance_system
    }

/** For tests: the row that picks [appearance]. */
internal fun appearanceTag(appearance: AppAppearance): String = "profile_appearance_${appearance.tag}"

/** Light first: the default (user decision 2026-10-14). */
private val APPEARANCES = listOf(AppAppearance.LIGHT, AppAppearance.DARK, AppAppearance.SYSTEM)

/** For tests: the button that picks [language]. */
internal fun languageTag(language: AppLanguage): String = "settings_language_${language.tag}"

/** Each language, named in its own language (the prototype's langBtn), Arabic first. */
private val LANGUAGES = listOf(AppLanguage.ARABIC, AppLanguage.ENGLISH)

private val CAN_WITHOUT_ACCOUNT =
    listOf(
        BahrIcons.TravelExplore to Res.string.profile_can_browse,
        BahrIcons.Timer to Res.string.profile_can_hold,
        BahrIcons.HourglassTop to Res.string.profile_can_waitlist,
    )

private val WHITESPACE = Regex("\\s+")
private val ARABIC_BLOCK = '؀'..'ۿ'
private const val LATIN_INITIALS = 2

/** The `providers` entry for Sign in with Apple (openapi `Account.providers`). */
internal const val APPLE_PROVIDER = "apple"
private const val APPLE_RELAY_DOMAIN = "@privaterelay.appleid.com"

/** For tests: the rows. */
internal const val ROW_PERSONAL_INFO = "profile_personal_info"
internal const val ROW_LANGUAGE = "profile_language"
internal const val ROW_APPEARANCE = "profile_appearance"
internal const val ROW_ABOUT = "profile_about"
internal const val ROW_SIGN_OUT = "settings_sign_out"
internal const val ROW_DELETE = "settings_delete_account"
