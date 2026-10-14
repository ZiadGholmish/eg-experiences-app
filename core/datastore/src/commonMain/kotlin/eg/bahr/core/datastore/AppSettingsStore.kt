package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.theme.AppAppearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The handful of settings that survive a restart.
 *
 * Language matters most: it decides both the layout direction and the
 * `Accept-Language` header that tells the backend which translation of a trip to
 * send. Since M3-M0b also the appearance (light, dark, or the phone's).
 */
class AppSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val languageKey = stringPreferencesKey("language")

    val language: Flow<AppLanguage> =
        dataStore.data.map { preferences ->
            AppLanguage.fromTag(preferences[languageKey])
        }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { preferences -> preferences[languageKey] = language.tag }
    }

    private val appearanceKey = stringPreferencesKey("appearance")

    /** Profile → Appearance (M3-M0b): Light until the person picks otherwise. */
    val appearance: Flow<AppAppearance> =
        dataStore.data.map { preferences -> AppAppearance.fromTag(preferences[appearanceKey]) }

    suspend fun setAppearance(appearance: AppAppearance) {
        dataStore.edit { preferences -> preferences[appearanceKey] = appearance.tag }
    }
}
