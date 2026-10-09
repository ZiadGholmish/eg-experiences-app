package eg.bahr.core.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveHoldStoreTest {
    private val first = StoredHold("BRL-7K4M2X9P", "+201012345678", "2026-10-09T22:42:30+03:00", "2026-10-09T22:27:30+03:00")
    private val second = first.copy(ref = "BRL-2B3C4D5F")

    @Test
    fun `nothing stored is no hold`() =
        runTest {
            assertNull(PreferencesActiveHoldStore(InMemoryPreferences()).hold.first())
        }

    @Test
    fun `a saved hold reads back whole and a new one replaces it`() =
        runTest {
            val store = PreferencesActiveHoldStore(InMemoryPreferences())

            store.save(first)
            assertEquals(first, store.hold.first())

            store.save(second)
            assertEquals(second, store.hold.first())
        }

    @Test
    fun `clear forgets the hold and its phone`() =
        runTest {
            val preferences = InMemoryPreferences()
            val store = PreferencesActiveHoldStore(preferences)
            store.save(first)

            store.clear(first.ref)

            assertNull(store.hold.first())
            // The phone is personal data: nothing of the hold stays in the file.
            assertEquals(emptyMap(), preferences.data.first().asMap())
        }

    @Test
    fun `clearing an older hold leaves a newer one alone`() =
        runTest {
            val store = PreferencesActiveHoldStore(InMemoryPreferences())
            store.save(second)

            store.clear(first.ref)

            assertEquals(second, store.hold.first())
        }
}
