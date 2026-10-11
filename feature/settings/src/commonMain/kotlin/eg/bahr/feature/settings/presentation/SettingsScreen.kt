package eg.bahr.feature.settings.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
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
import eg.bahr.core.localization.generated.resources.settings_language
import eg.bahr.core.localization.generated.resources.settings_language_arabic
import eg.bahr.core.localization.generated.resources.settings_language_english
import eg.bahr.core.localization.generated.resources.settings_title
import eg.bahr.core.localization.generated.resources.settings_version
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
                            inactiveContainerColor = BahrTheme.colors.surfaceLowest,
                        ),
                    modifier = Modifier.heightIn(min = BahrSpacing.minTouch).testTag(languageTag(language)),
                ) {
                    Text(text = stringResource(name), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

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

/** For tests: the button that picks [language]. */
internal fun languageTag(language: AppLanguage): String = "settings_language_${language.tag}"

/** Each language, named in its own language (the prototype's langBtn), Arabic first. */
private val LANGUAGES =
    listOf(
        AppLanguage.ARABIC to Res.string.settings_language_arabic,
        AppLanguage.ENGLISH to Res.string.settings_language_english,
    )
