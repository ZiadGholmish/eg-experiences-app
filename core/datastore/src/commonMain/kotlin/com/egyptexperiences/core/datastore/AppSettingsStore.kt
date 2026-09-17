package com.egyptexperiences.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.egyptexperiences.core.common.locale.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The handful of settings that survive a restart.
 *
 * Language is the only one in phase 1, and it is the one that matters most: it
 * decides both the layout direction and the `Accept-Language` header that tells
 * the backend which translation of a trip to send.
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
}
