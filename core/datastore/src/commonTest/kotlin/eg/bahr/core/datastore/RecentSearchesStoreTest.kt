package eg.bahr.core.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentSearchesStoreTest {
    @Test
    fun `nothing stored is no searches`() =
        runTest {
            assertTrue(PreferencesRecentSearchesStore(InMemoryPreferences()).searches.first().isEmpty())
        }

    @Test
    fun `newest first - a repeat moves up instead of repeating`() =
        runTest {
            val store = PreferencesRecentSearchesStore(InMemoryPreferences())

            store.add("فلوكة")
            store.add("birds")
            store.add("فلوكة")

            assertEquals(listOf("فلوكة", "birds"), store.searches.first())
        }

    @Test
    fun `only the last five are kept`() =
        runTest {
            val store = PreferencesRecentSearchesStore(InMemoryPreferences())

            (1..7).forEach { store.add("search $it") }

            assertEquals((7 downTo 3).map { "search $it" }, store.searches.first())
            assertEquals(MAX_RECENT_SEARCHES, store.searches.first().size)
        }

    @Test
    fun `outer spaces are dropped - blank is ignored - the text is otherwise kept as typed`() =
        runTest {
            val store = PreferencesRecentSearchesStore(InMemoryPreferences())

            store.add("   ")
            store.add("  بُرج  البرلس ")

            assertEquals(listOf("بُرج  البرلس"), store.searches.first())
        }

    @Test
    fun `clear forgets them all`() =
        runTest {
            val store = PreferencesRecentSearchesStore(InMemoryPreferences())
            store.add("birds")

            store.clear()

            assertTrue(store.searches.first().isEmpty())
        }

    @Test
    fun `an entry that does not parse reads as none`() =
        runTest {
            val broken = mutablePreferencesOf(stringPreferencesKey("recent_trip_searches") to "{not a list")
            val store = PreferencesRecentSearchesStore(InMemoryPreferences(broken))

            assertTrue(store.searches.first().isEmpty())
            store.add("birds")
            assertEquals(listOf("birds"), store.searches.first())
        }
}
