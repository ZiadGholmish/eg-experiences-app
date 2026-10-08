package eg.bahr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.navigation.AppNavHost
import org.koin.compose.koinInject

/**
 * The composition root.
 *
 * Language is read before anything is drawn so the first frame is already in
 * the right script and layout direction — Arabic is the default, so getting
 * this wrong is the common case, not an edge one.
 */
@Composable
fun App() {
    val settings: AppSettingsStore = koinInject()
    val language by settings.language.collectAsStateWithLifecycle(initialValue = AppLanguage.default)

    ProvideAppLanguage(language) {
        BahrTheme(locale = language.toBahrLocale()) {
            AppNavHost()
        }
    }
}

/** The stored language decides the theme's script, type rules and direction, not the device. */
private fun AppLanguage.toBahrLocale(): BahrLocale =
    when (this) {
        AppLanguage.ARABIC -> BahrLocale.Arabic
        AppLanguage.ENGLISH -> BahrLocale.English
    }
