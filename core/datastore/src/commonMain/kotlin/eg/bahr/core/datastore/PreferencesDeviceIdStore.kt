package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eg.bahr.core.common.auth.DeviceIdSource
import kotlinx.coroutines.flow.first
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The per-install device id (openapi `DeviceId`, M3-B1): a random UUID made on first use and kept in
 * the preferences file. Not secret (it only links this device's anonymous holds and waiting-list
 * entries to the account it signs in to), so plain preferences. A reinstall makes a new one.
 */
class PreferencesDeviceIdStore(
    private val dataStore: DataStore<Preferences>,
) : DeviceIdSource {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun deviceId(): String {
        dataStore.data.first()[key]?.let { return it }
        var id = ""
        // Inside one edit, so two first requests at once agree on one id.
        dataStore.edit { preferences ->
            id = preferences[key] ?: Uuid.random().toString().also { preferences[key] = it }
        }
        return id
    }

    private companion object {
        val key = stringPreferencesKey("device_id")
    }
}
