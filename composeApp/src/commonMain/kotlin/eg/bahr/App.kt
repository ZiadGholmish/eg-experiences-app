package eg.bahr

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.designsystem.components.BahrNavigationBar
import eg.bahr.core.designsystem.components.bahrNavigationSpokenLabel
import eg.bahr.core.designsystem.error.BahrErrorHost
import eg.bahr.core.designsystem.error.BahrErrorMessage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.BahrThemeName
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.messageRes
import eg.bahr.deeplink.AppDeepLinkInbox
import eg.bahr.di.AppAppearanceState
import eg.bahr.di.AppLanguageState
import eg.bahr.feature.auth.navigation.CompleteProfileSheetSlot
import eg.bahr.navigation.AppNavHost
import eg.bahr.navigation.AppTab
import eg.bahr.navigation.AppTabs
import eg.bahr.navigation.AppTabsState
import eg.bahr.navigation.badgeCount
import eg.bahr.navigation.title
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The composition root on Android: the whole app, bottom bar included (M4-M8).
 *
 * The selected tab lives here, above the language: a language switch redraws everything below in the
 * new script and direction (and starts each tab's stack over, which also reloads its data in the new
 * `Accept-Language`), but the reader stays on the tab they switched from: Profile.
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
 * What the native iOS bar shows (M4-M8; the M3-M0b tabs), all resolved here so Swift keeps no copy and
 * no colour: the language (for the bar's direction), each tab's label, what a screen reader should say
 * for it (the unread count inside, handoff §1), its badge text ("3", "9+", or null), the selected tint
 * (`onPrimaryContainer`, as the Compose bar's selected icon) and the muted tint of the others.
 */
data class IosBarContent(
    val language: AppLanguage,
    val titles: List<String>,
    val spokenLabels: List<String>,
    val badges: List<String?>,
    val selectedTint: Color,
    val unselectedTint: Color,
)

/**
 * The composition root of one tab on iOS (M4-M8): SwiftUI's `TabView` hosts five of these, one per
 * tab, each with its own NavHost, all over the one Koin graph and the one [tabs] state. Every root sends
 * the bar's content ([onBarContent]); Swift ignores repeats. The selected tab's root also hosts
 * "Complete your profile" (M3-M0b), so the sheet shows once, over what is on screen.
 */
@Composable
fun App(
    tab: AppTab,
    tabs: AppTabsState,
    appVersion: String,
    onBarContent: (IosBarContent) -> Unit,
) {
    App {
        val language by koinInject<AppLanguageState>().language.collectAsStateWithLifecycle()
        val titles = AppTab.entries.map { stringResource(it.title) }
        val spoken = AppTab.entries.mapIndexed { index, it -> bahrNavigationSpokenLabel(titles[index], it.badgeCount) }
        val content =
            IosBarContent(
                language = language,
                titles = titles,
                spokenLabels = spoken,
                badges = AppTab.entries.map { BahrNavigationBar.badgeText(it.badgeCount) },
                selectedTint = MaterialTheme.colorScheme.onPrimaryContainer,
                // As Android's bar: the tabs not selected are muted, so the bar stays calm.
                unselectedTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        LaunchedEffect(content) { onBarContent(content) }
        AppNavHost(tab = tab, tabs = tabs, appVersion = appVersion)
        val state by tabs.state.collectAsStateWithLifecycle()
        if (state.selected == tab) CompleteProfileSheetSlot()
    }
}

/**
 * Language, theme and the error host around [content].
 *
 * The language is read from the process-wide [AppLanguageState] before anything is drawn, so the first
 * frame is already in the right script and layout direction — Arabic is the default, so getting this
 * wrong is the common case, not an edge one — and a switch (Profile, a link's `?lang=`) reaches every
 * root at once.
 */
@Composable
private fun App(content: @Composable () -> Unit) {
    val languages: AppLanguageState = koinInject()
    val language by languages.language.collectAsStateWithLifecycle()
    // Light unless the person picked Dark, or the phone's setting (System) in Profile (M3-M0b).
    val appearance by koinInject<AppAppearanceState>().appearance.collectAsStateWithLifecycle()
    val theme = if (appearance.isDark(isSystemInDarkTheme())) BahrThemeName.Dark else BahrThemeName.LakeBurullus

    // App-scoped (Koin single), so a message on screen survives the language switch's re-keyed
    // composition and an Activity recreation: it leaves the queue only on dismiss.
    val errors: AppErrorController = koinInject()
    val pendingError by errors.current.collectAsStateWithLifecycle(initialValue = null)

    ProvideAppLanguage(language) {
        BahrTheme(locale = language.toBahrLocale(), theme = theme) {
            // Inside the theme and the language: the snackbar is themed, mirrored and localized.
            BahrErrorHost(
                message = pendingError?.let { BahrErrorMessage(id = it.id, text = it.error.messageRes()) },
                onDismissed = { shown -> errors.dismiss(shown.id) },
            ) {
                // The page's off-white (M4-M12) under every screen, before the tab Scaffold paints it too.
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    content()
                }
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
        // A tab saved by an older build (M3-M0b removed Search and renamed Settings) restores as Home.
        restore = { saved ->
            AppTabsState(initial = AppTab.entries.firstOrNull { it.name == saved[0] } ?: AppTab.Home, splashDone = saved[1] as Boolean)
        },
    )
