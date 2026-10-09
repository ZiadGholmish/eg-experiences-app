package eg.bahr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.designsystem.error.BahrErrorHost
import eg.bahr.core.designsystem.error.BahrErrorMessage
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.localization.messageRes
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
                AppNavHost()
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
