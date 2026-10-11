package eg.bahr

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.colors
import eg.bahr.core.designsystem.error.BahrErrorHost
import eg.bahr.core.designsystem.error.BahrErrorMessage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.messageRes
import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.di.AppLanguageState
import eg.bahr.navigation.AppNavHost
import eg.bahr.navigation.AppTab
import eg.bahr.navigation.AppTabs
import eg.bahr.navigation.AppTabsState
import eg.bahr.navigation.title
import eg.bahr.navigation.tone
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The composition root on Android: the whole app, bottom bar included (M4-M8).
 *
 * The selected tab lives here, above the language: a language switch redraws everything below in the
 * new script and direction (and starts each tab's stack over, which also reloads its data in the new
 * `Accept-Language`), but the reader stays on the tab they switched from: Settings.
 */
@Composable
fun App(appVersion: String) {
    val tabs = rememberSaveable(saver = TabsSaver) { AppTabsState() }

    // A link from outside opens in the Home tab's stack (its NavHost opens it).
    val pendingLink by AppDeepLinkInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLink) { if (pendingLink != null) tabs.select(AppTab.Home) }

    // A language switch starts every tab over at its root (see AppTabsState.resetToRoots).
    val languages: AppLanguageState = koinInject()
    LaunchedEffect(languages, tabs) { languages.language.drop(1).collect { tabs.resetToRoots() } }

    App { AppTabs(tabs = tabs, appVersion = appVersion) }
}

/**
 * The composition root of one tab on iOS (M4-M8): SwiftUI's `TabView` hosts four of these, one per
 * tab, each with its own NavHost, all over the one Koin graph and the one [tabs] state.
 *
 * [onBarContent] hands Swift what its bar shows: the language (for its layout direction), the four tab
 * labels, resolved here from core:localization so no translation is kept in Swift, and each tab's
 * colour when selected (its tone's content colour in the current theme) and the muted colour of the
 * others, so no colour is kept there either. Every root sends the same values; Swift ignores repeats.
 */
@Composable
fun App(
    tab: AppTab,
    tabs: AppTabsState,
    appVersion: String,
    onBarContent: (language: AppLanguage, titles: List<String>, tints: List<Color>, unselectedTint: Color) -> Unit,
) {
    App { language ->
        val titles = AppTab.entries.map { stringResource(it.title) }
        val tints = AppTab.entries.map { it.tone.colors().second }
        // As Android's bar: the tabs not selected are muted, so the bar stays calm.
        val unselected = MaterialTheme.colorScheme.onSurfaceVariant
        LaunchedEffect(language, titles, tints, unselected) { onBarContent(language, titles, tints, unselected) }
        AppNavHost(tab = tab, tabs = tabs, appVersion = appVersion)
    }
}

/**
 * Language, theme and the error host around [content], for both roots.
 *
 * The language is read from the process-wide [AppLanguageState] before anything is drawn, so the first
 * frame is already in the right script and layout direction — Arabic is the default, so getting this
 * wrong is the common case, not an edge one — and a switch (Settings, a link's `?lang=`) reaches every
 * root at once.
 */
@Composable
private fun App(content: @Composable (AppLanguage) -> Unit) {
    val languages: AppLanguageState = koinInject()
    val language by languages.language.collectAsStateWithLifecycle()

    // App-scoped (Koin single), so a message on screen survives the language switch's re-keyed
    // composition and an Activity recreation: it leaves the queue only on dismiss.
    val errors: AppErrorController = koinInject()
    val pendingError by errors.current.collectAsStateWithLifecycle(initialValue = null)

    ProvideAppLanguage(language) {
        BahrTheme(locale = language.toBahrLocale()) {
            // Inside the theme and the language: the snackbar is themed, mirrored and localized.
            BahrErrorHost(
                message = pendingError?.let { BahrErrorMessage(id = it.id, text = it.error.messageRes()) },
                onDismissed = { shown -> errors.dismiss(shown.id) },
            ) {
                content(language)
            }
        }
    }
}

/** The stored language decides the theme's script, type rules and direction, not the device. */
private fun AppLanguage.toBahrLocale(): BahrLocale =
    when (this) {
        AppLanguage.ARABIC -> BahrLocale.Arabic
        AppLanguage.ENGLISH -> BahrLocale.English
    }

/**
 * The selected tab and whether splash has run survive an Activity recreation and process death. Each
 * tab's back stack is saved by the tab host itself.
 */
private val TabsSaver: Saver<AppTabsState, List<Any>> =
    Saver(
        save = { listOf(it.selected.name, it.splashDone) },
        restore = { saved -> AppTabsState(initial = AppTab.valueOf(saved[0] as String), splashDone = saved[1] as Boolean) },
    )
