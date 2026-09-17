package com.egyptexperiences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egyptexperiences.core.common.locale.AppLanguage
import com.egyptexperiences.core.datastore.AppSettingsStore
import com.egyptexperiences.core.designsystem.theme.EgTheme
import com.egyptexperiences.core.localization.ProvideAppLanguage
import com.egyptexperiences.navigation.AppNavHost
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
        EgTheme {
            AppNavHost()
        }
    }
}
