package eg.bahr.feature.settings.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.format.BahrFormat
import eg.bahr.core.designsystem.icon.BahrIcons
import eg.bahr.core.designsystem.theme.BahrSize
import eg.bahr.core.designsystem.theme.BahrSpacing
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.settings_about_line
import eg.bahr.core.localization.generated.resources.settings_account
import eg.bahr.core.localization.generated.resources.settings_delete_account
import eg.bahr.core.localization.generated.resources.settings_language
import eg.bahr.core.localization.generated.resources.settings_language_arabic
import eg.bahr.core.localization.generated.resources.settings_language_english
import eg.bahr.core.localization.generated.resources.settings_sign_in
import eg.bahr.core.localization.generated.resources.settings_sign_in_note
import eg.bahr.core.localization.generated.resources.settings_sign_out
import eg.bahr.core.localization.generated.resources.settings_signed_in
import eg.bahr.core.localization.generated.resources.settings_signed_in_as
import eg.bahr.core.localization.generated.resources.settings_title
import eg.bahr.core.localization.generated.resources.settings_version
import eg.bahr.core.network.auth.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings (M4-M8), the Settings tab's root. The handoff has no Settings screen; the user picked the
 * layout (option B, 2026-10-11): a title, "Language" with the two languages as a segmented control on
 * one row (each name in its own language), then the about line and the version.
 *
 * A pick only stores the language. Every Compose root reads the store and redraws in it (script,
 * direction, strings), this one included, so the screen does nothing else.
 *
 * [scrollToTop] fires when the selected Settings tab is tapped again.
 */
@Composable
internal fun SettingsScreen(
    appVersion: String,
    modifier: Modifier = Modifier,
    onSignIn: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    scrollToTop: Flow<Unit> = emptyFlow(),
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    ScrollsToTop(scroll, scrollToTop)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = BahrSpacing.gutter, vertical = BahrSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.md),
    ) {
        Text(
            text = stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }.padding(bottom = BahrSpacing.sm),
        )

        SectionTitle(Res.string.settings_language, BahrIcons.Translate)
        // User decision 2026-10-11 (option B): the two languages side by side on one row.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LANGUAGES.forEachIndexed { index, (language, name) ->
                SegmentedButton(
                    selected = state.language == language,
                    onClick = { viewModel.selectLanguage(language) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = LANGUAGES.size),
                    colors =
                        SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            // White on the off-white page (M4-M12).
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    modifier = Modifier.heightIn(min = BahrSpacing.minTouch).testTag(languageTag(language)),
                ) {
                    Text(text = stringResource(name), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // The account (M3-M0): sign in, or who is signed in with sign out and delete account.
        SectionTitle(Res.string.settings_account, BahrIcons.Person, Modifier.padding(top = BahrSpacing.md))
        AccountSection(
            account = state.account,
            signingOut = state.signingOut,
            onSignIn = onSignIn,
            onSignOut = viewModel::signOut,
            onDeleteAccount = onDeleteAccount,
        )

        // The about line, then the version (no contact line until there is an address to give).
        Text(
            text = stringResource(Res.string.settings_about_line),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = BahrSpacing.lg),
        )
        // A build name (0.1-local) reads left to right inside Arabic too.
        Text(
            text = stringResource(Res.string.settings_version, BahrFormat.ltr(appVersion)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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

@Composable
private fun SectionTitle(
    title: StringResource,
    icon: BahrIcons,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = BahrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BahrSpacing.sm),
    ) {
        Icon(
            imageVector = icon.outlined(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(BahrSize.iconMedium),
        )
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The account rows in one white card. Signed out: "Sign in" and why. Signed in: "Signed in as …", then
 * "Sign out" and "Delete account" (drawn in `error`; the dialog that confirms it is feature:auth's).
 * Nothing while the store has not answered, so a signed-in reader never sees "Sign in" flash.
 */
@Composable
private fun AccountSection(
    account: SessionState,
    signingOut: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().clip(BahrTheme.shapes.extraLarge).background(c.surface),
    ) {
        when (account) {
            SessionState.Unknown -> Unit
            SessionState.SignedOut ->
                AccountRow(
                    text = stringResource(Res.string.settings_sign_in),
                    note = stringResource(Res.string.settings_sign_in_note),
                    color = c.primary,
                    tag = ACCOUNT_SIGN_IN_TAG,
                    onClick = onSignIn,
                )
            is SessionState.SignedIn -> {
                Text(
                    text =
                        account.accountName?.let { stringResource(Res.string.settings_signed_in_as, it) }
                            ?: stringResource(Res.string.settings_signed_in),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurface,
                    modifier = Modifier.padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
                )
                HorizontalDivider(color = c.outlineVariant)
                AccountRow(
                    text = stringResource(Res.string.settings_sign_out),
                    color = c.primary,
                    enabled = !signingOut,
                    tag = ACCOUNT_SIGN_OUT_TAG,
                    onClick = onSignOut,
                )
                HorizontalDivider(color = c.outlineVariant)
                AccountRow(
                    text = stringResource(Res.string.settings_delete_account),
                    color = c.error,
                    tag = ACCOUNT_DELETE_TAG,
                    onClick = onDeleteAccount,
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    tag: String,
    onClick: () -> Unit,
    note: String? = null,
    enabled: Boolean = true,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = BahrSpacing.minTouch)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(tag)
                .padding(horizontal = BahrSpacing.lg, vertical = BahrSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BahrSpacing.xs),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
        note?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** For tests: the account rows. */
internal const val ACCOUNT_SIGN_IN_TAG = "settings_sign_in"
internal const val ACCOUNT_SIGN_OUT_TAG = "settings_sign_out"
internal const val ACCOUNT_DELETE_TAG = "settings_delete_account"

/** For tests: the button that picks [language]. */
internal fun languageTag(language: AppLanguage): String = "settings_language_${language.tag}"

/** Each language, named in its own language (the prototype's langBtn), Arabic first. */
private val LANGUAGES =
    listOf(
        AppLanguage.ARABIC to Res.string.settings_language_arabic,
        AppLanguage.ENGLISH to Res.string.settings_language_english,
    )
