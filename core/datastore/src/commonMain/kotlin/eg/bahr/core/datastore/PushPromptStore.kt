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

    /**
     * The same count for the pre-prompt on a hold just placed (M3-M7, the hold reminder): counted apart, since a "no"
     * to seat alerts says nothing about a reminder before held seats are released. The OS dialog is one fact for both.
     */
    suspend fun holdReminderDeclines(): Int

    suspend fun recordHoldReminderDecline()

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

    override suspend fun holdReminderDeclines(): Int = dataStore.data.first()[holdDeclinesKey] ?: 0

    override suspend fun recordHoldReminderDecline() {
        dataStore.edit { preferences -> preferences[holdDeclinesKey] = (preferences[holdDeclinesKey] ?: 0) + 1 }
    }

    override suspend fun systemDialogShown(): Boolean = dataStore.data.first()[askedKey] ?: false

    override suspend fun recordSystemDialogShown() {
        dataStore.edit { preferences -> preferences[askedKey] = true }
    }

    private companion object {
        val declinesKey = intPreferencesKey("push_prompt_declines")
        val askedKey = booleanPreferencesKey("push_system_dialog_shown")
        val holdDeclinesKey = intPreferencesKey("push_prompt_hold_declines")
    }
}
