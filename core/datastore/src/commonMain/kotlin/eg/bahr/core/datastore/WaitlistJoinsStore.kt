package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A sold-out date this device put an email on the waiting list of (PLAN M4-M5; an email since M4-M13,
 * a phone before), so the trip page can
 * say "On the waiting list" after a restart instead of offering the form again. The backend has no
 * anonymous "my waiting lists" read; once the user is identified by OTP, M6-M1 lists the server's.
 *
 * Primitives only, as the server sent or the user typed them: [date] is the departure's ISO date
 * (`2026-10-24`, a Cairo calendar date) and [joinedAt] an ISO-8601 instant. The feature parses them.
 */
@Serializable
data class StoredWaitlistJoin(
    val departureId: String,
    val tripSlug: String,
    val date: String,
    val partySize: Int,
    val email: String,
    val joinedAt: String,
)

/**
 * The waiting-list joins this device made, one per departure.
 *
 * [StoredWaitlistJoin.email] is personal data in a plain preferences file, so an entry is dropped as
 * soon as it stops meaning anything: its date has passed, the date became bookable again, or it was
 * cancelled or closed (the trip feature decides, see its `WaitlistMemory`).
 */
interface WaitlistJoinsStore {
    /** Oldest join first; empty when there are none. */
    val joins: Flow<List<StoredWaitlistJoin>>

    /**
     * Remembers [join], replacing an earlier one for the same departure: a repeat join is the same
     * entry on the server too, with the latest party size (openapi `joinWaitlist`). Past
     * [MAX_STORED_WAITLIST_JOINS], the oldest falls off.
     */
    suspend fun save(join: StoredWaitlistJoin)

    /**
     * Forgets the joins for these departures; ids not stored are ignored. Also erases what an older
     * build left that this one cannot show (the phone joins from before M4-M13), so `remove(emptySet())`
     * is a valid "tidy up" call.
     */
    suspend fun remove(departureIds: Set<String>)
}

/**
 * A bound on a list nobody would reach by hand, so a bug that saves in a loop cannot grow the
 * preferences file without end.
 */
const val MAX_STORED_WAITLIST_JOINS = 20

/**
 * One preferences key holding a JSON array, as [PreferencesRecentSearchesStore] does. A payload that
 * no longer parses reads as no joins, never a crash; a field a later build adds is ignored.
 *
 * The joins before M4-M13 were by phone, under another key. Their server entries are gone (the
 * backend's V18 deleted every phone entry), so they are not migrated: they are never read, and every
 * write (including an empty [remove]) erases that key, so the old phone numbers leave the file.
 */
class PreferencesWaitlistJoinsStore(
    private val dataStore: DataStore<Preferences>,
) : WaitlistJoinsStore {
    override val joins: Flow<List<StoredWaitlistJoin>> = dataStore.data.map { preferences -> decode(preferences[key]) }

    override suspend fun save(join: StoredWaitlistJoin) {
        dataStore.edit { preferences ->
            preferences.remove(phoneJoinsKey)
            val others = decode(preferences[key]).filter { it.departureId != join.departureId }
            preferences[key] = json.encodeToString(serializer, (others + join).takeLast(MAX_STORED_WAITLIST_JOINS))
        }
    }

    override suspend fun remove(departureIds: Set<String>) {
        dataStore.edit { preferences ->
            preferences.remove(phoneJoinsKey)
            val stored = decode(preferences[key])
            val kept = stored.filter { it.departureId !in departureIds }
            when {
                kept.size == stored.size -> Unit
                kept.isEmpty() -> preferences.remove(key)
                else -> preferences[key] = json.encodeToString(serializer, kept)
            }
        }
    }

    private fun decode(raw: String?): List<StoredWaitlistJoin> =
        raw?.let {
            try {
                json.decodeFromString(serializer, it)
            } catch (_: SerializationException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
        } ?: emptyList()

    private companion object {
        val key = stringPreferencesKey("waitlist_joins_email")

        /** Where the phone joins of M4-M5..M4-M12 were kept: only ever erased now. */
        val phoneJoinsKey = stringPreferencesKey("waitlist_joins")
        val serializer = ListSerializer(StoredWaitlistJoin.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
