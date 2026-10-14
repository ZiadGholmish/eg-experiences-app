package eg.bahr.core.datastore

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.StoredSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M3-M0 review #4: what both secure stores share, and the per-install device id. */
class SessionStorageTest {
    private val session =
        StoredSession(
            tokens = AuthTokens("a1", "2026-10-14T10:15:00+03:00", "r1", "2026-11-13T10:00:00+03:00"),
            accountName = "سارة علي",
            profileComplete = true,
            providers = listOf("apple", "google"),
        )

    @Test
    fun `the session survives the codec`() {
        assertEquals(session, SessionCodec.decode(SessionCodec.encode(session)))
    }

    @Test
    fun `what does not parse reads as signed out - never a crash`() {
        assertNull(SessionCodec.decode(null))
        assertNull(SessionCodec.decode("not json"))
        assertNull(SessionCodec.decode("""{"tokens":{"accessToken":"a1"}}"""), "a token set missing its parts")
    }

    @Test
    fun `a field a later build adds is ignored`() {
        val later = SessionCodec.encode(session).removeSuffix("}") + ""","addedLater":true}"""
        assertEquals(session, SessionCodec.decode(later))
    }

    @Test
    fun `the device id is made once and then stays`() =
        runTest {
            val preferences = InMemoryPreferences()
            val first = PreferencesDeviceIdStore(preferences).deviceId()

            assertEquals(first, PreferencesDeviceIdStore(preferences).deviceId(), "the same file, the same id")
            assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(first), "a UUID: $first")
            assertNotEquals(first, PreferencesDeviceIdStore(InMemoryPreferences()).deviceId(), "another install, another id")
        }
}
