package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The seat hold this device placed and has not yet seen end, so it survives an app restart and Home
 * can offer "Continue your booking" (PLAN M2-M4).
 *
 * Just enough to read the booking again, nothing to show: [ref] and [guestPhone] are what an
 * anonymous `GET /bookings/{ref}?phone=` needs; [holdExpiresAt] and [serverNow] are the server's
 * instants (ISO-8601) from the answer that placed the hold, the countdown's full length.
 * Everything a screen shows comes from that fresh read, never from here.
 */
data class StoredHold(
    val ref: String,
    val guestPhone: String,
    val holdExpiresAt: String,
    val serverNow: String,
)

/**
 * One hold at a time: saving replaces whatever was stored.
 *
 * [guestPhone] is personal data in a plain preferences file, so the hold is cleared as soon as the
 * server says it is over (expired, cancelled, confirmed) or it is released.
 */
interface ActiveHoldStore {
    /** The stored hold, or null. */
    val hold: Flow<StoredHold?>

    suspend fun save(hold: StoredHold)

    /**
     * Forgets the hold if it is still [ref]. Keyed, so a screen finishing an old hold cannot wipe a
     * newer one placed since.
     */
    suspend fun clear(ref: String)
}

class PreferencesActiveHoldStore(
    private val dataStore: DataStore<Preferences>,
) : ActiveHoldStore {
    override val hold: Flow<StoredHold?> =
        dataStore.data.map { preferences ->
            // All four or nothing: a half-written or half-cleared entry is no hold.
            StoredHold(
                ref = preferences[refKey] ?: return@map null,
                guestPhone = preferences[phoneKey] ?: return@map null,
                holdExpiresAt = preferences[expiresKey] ?: return@map null,
                serverNow = preferences[serverNowKey] ?: return@map null,
            )
        }

    override suspend fun save(hold: StoredHold) {
        dataStore.edit { preferences ->
            preferences[refKey] = hold.ref
            preferences[phoneKey] = hold.guestPhone
            preferences[expiresKey] = hold.holdExpiresAt
            preferences[serverNowKey] = hold.serverNow
        }
    }

    override suspend fun clear(ref: String) {
        dataStore.edit { preferences ->
            if (preferences[refKey] == ref) {
                preferences.remove(refKey)
                preferences.remove(phoneKey)
                preferences.remove(expiresKey)
                preferences.remove(serverNowKey)
            }
        }
    }

    private companion object {
        val refKey = stringPreferencesKey("active_hold_ref")
        val phoneKey = stringPreferencesKey("active_hold_phone")
        val expiresKey = stringPreferencesKey("active_hold_expires_at")
        val serverNowKey = stringPreferencesKey("active_hold_server_now")
    }
}
