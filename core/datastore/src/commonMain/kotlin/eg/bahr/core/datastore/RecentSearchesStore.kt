package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The trip searches this device ran (PLAN M4-M3), newest first, at most [MAX_RECENT_SEARCHES], so the
 * search screen can offer them again. Only the text the user typed, nothing about the results.
 */
interface RecentSearchesStore {
    /** Newest first; empty when there are none. */
    val searches: Flow<List<String>>

    /**
     * Puts [query] first. A search already in the list moves up rather than repeating; the oldest
     * falls off past [MAX_RECENT_SEARCHES]. A blank one is ignored.
     */
    suspend fun add(query: String)

    suspend fun clear()
}

/** How many recent searches the search screen offers. */
const val MAX_RECENT_SEARCHES = 5

/**
 * One preferences key holding a JSON array: a string-set key would lose the order, and the order is
 * the point. An entry that no longer parses (a future format) reads as no searches, not a crash.
 */
class PreferencesRecentSearchesStore(
    private val dataStore: DataStore<Preferences>,
) : RecentSearchesStore {
    override val searches: Flow<List<String>> = dataStore.data.map { preferences -> decode(preferences[key]) }

    override suspend fun add(query: String) {
        // Outer spaces only: the text is otherwise kept exactly as typed (the server folds spelling).
        val entry = query.trim()
        if (entry.isEmpty()) return
        dataStore.edit { preferences ->
            val updated = (listOf(entry) + decode(preferences[key]).filter { it != entry }).take(MAX_RECENT_SEARCHES)
            preferences[key] = json.encodeToString(serializer, updated)
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(key) }
    }

    private fun decode(raw: String?): List<String> =
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
        val key = stringPreferencesKey("recent_trip_searches")
        val serializer = ListSerializer(String.serializer())
        val json = Json
    }
}
