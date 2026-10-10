package eg.bahr.core.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaitlistJoinsStoreTest {
    private fun join(
        departureId: String,
        partySize: Int = 2,
    ) = StoredWaitlistJoin(
        departureId = departureId,
        tripSlug = "burullus-dawn",
        date = "2026-10-24",
        partySize = partySize,
        phone = "+201001234567",
        joinedAt = "2026-10-10T09:00:00Z",
    )

    @Test
    fun `nothing stored is no joins`() =
        runTest {
            assertTrue(PreferencesWaitlistJoinsStore(InMemoryPreferences()).joins.first().isEmpty())
        }

    @Test
    fun `a join is kept with every field`() =
        runTest {
            val store = PreferencesWaitlistJoinsStore(InMemoryPreferences())

            store.save(join("dep-3"))

            assertEquals(listOf(join("dep-3")), store.joins.first())
        }

    @Test
    fun `a repeat join for the same departure replaces the earlier one`() =
        runTest {
            val store = PreferencesWaitlistJoinsStore(InMemoryPreferences())
            store.save(join("dep-3", partySize = 2))
            store.save(join("dep-5"))

            store.save(join("dep-3", partySize = 4))

            assertEquals(listOf(join("dep-5"), join("dep-3", partySize = 4)), store.joins.first())
        }

    @Test
    fun `remove forgets only the given departures`() =
        runTest {
            val store = PreferencesWaitlistJoinsStore(InMemoryPreferences())
            store.save(join("dep-3"))
            store.save(join("dep-5"))

            store.remove(setOf("dep-3", "not-stored"))

            assertEquals(listOf(join("dep-5")), store.joins.first())
            store.remove(setOf("dep-5"))
            assertTrue(store.joins.first().isEmpty())
        }

    @Test
    fun `only the newest are kept past the cap`() =
        runTest {
            val store = PreferencesWaitlistJoinsStore(InMemoryPreferences())

            (1..MAX_STORED_WAITLIST_JOINS + 2).forEach { store.save(join("dep-$it")) }

            val kept = store.joins.first()
            assertEquals(MAX_STORED_WAITLIST_JOINS, kept.size)
            assertEquals("dep-3", kept.first().departureId)
        }

    @Test
    fun `a payload that does not parse reads as none`() =
        runTest {
            val broken = mutablePreferencesOf(stringPreferencesKey("waitlist_joins") to "{not a list")
            val store = PreferencesWaitlistJoinsStore(InMemoryPreferences(broken))

            assertTrue(store.joins.first().isEmpty())
            store.save(join("dep-3"))
            assertEquals(listOf(join("dep-3")), store.joins.first())
        }

    @Test
    fun `a field a later build adds is ignored`() =
        runTest {
            val later =
                """[{"departureId":"dep-3","tripSlug":"burullus-dawn","date":"2026-10-24","partySize":2,""" +
                    """"phone":"+201001234567","joinedAt":"2026-10-10T09:00:00Z","serverEntryId":"w-1"}]"""
            val store =
                PreferencesWaitlistJoinsStore(
                    InMemoryPreferences(
                        mutablePreferencesOf(
                            stringPreferencesKey("waitlist_joins") to later,
                        ),
                    ),
                )

            assertEquals(listOf(join("dep-3")), store.joins.first())
        }
}
