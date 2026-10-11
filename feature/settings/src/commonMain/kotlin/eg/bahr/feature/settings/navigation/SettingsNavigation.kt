package eg.bahr.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eg.bahr.feature.settings.presentation.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/** Settings (M4-M8): the Settings tab's root. */
@Serializable
data object SettingsRoute

/**
 * The settings feature's public surface. [appVersion] is the build's version name, which only the app
 * module knows (Android's BuildConfig, iOS's Info.plist). [scrollToTop] fires when the selected tab is
 * tapped again.
 */
fun NavGraphBuilder.settingsScreen(
    appVersion: String,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    composable<SettingsRoute> {
        SettingsScreen(appVersion = appVersion, scrollToTop = scrollToTop)
    }
}
