package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * What the push pre-prompt (M3-M5, handoff §3) remembers between waiting-list joins: how many times the person said
 * "Not now" (it stops asking after two), and whether the OS dialog was ever shown (on Android 13+ a permission that is
 * not granted, was asked for and has no rationale left is denied for good, so "Yes" opens the settings instead).
 */
interface PushPromptStore {
    suspend fun declines(): Int

    suspend fun recordDecline()

    suspend fun systemDialogShown(): Boolean

    suspend fun recordSystemDialogShown()
}

class PreferencesPushPromptStore(
    private val dataStore: DataStore<Preferences>,
) : PushPromptStore {
    override suspend fun declines(): Int = dataStore.data.first()[declinesKey] ?: 0

    override suspend fun recordDecline() {
        dataStore.edit { preferences -> preferences[declinesKey] = (preferences[declinesKey] ?: 0) + 1 }
    }

    override suspend fun systemDialogShown(): Boolean = dataStore.data.first()[askedKey] ?: false

    override suspend fun recordSystemDialogShown() {
        dataStore.edit { preferences -> preferences[askedKey] = true }
    }

    private companion object {
        val declinesKey = intPreferencesKey("push_prompt_declines")
        val askedKey = booleanPreferencesKey("push_system_dialog_shown")
    }
}
