package eg.bahr.core.datastore

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.StoredSession
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * M3-M0 review #4: the real Keychain on the iOS simulator, on an item and a preferences key of the
 * test's own, so a developer's signed-in simulator app is never touched.
 *
 * Gradle runs the Kotlin/Native tests as a bare process (`simctl spawn`), outside any app, and such a
 * process has no Keychain: every call answers errSecNotAvailable (-25291). There the tests say so and
 * stop; the Keychain is exercised by the app itself (the step report's kill-and-relaunch check). They
 * run in full wherever a Keychain is available (a test host app, a device).
 */
class KeychainSessionStoreTest {
    private companion object {
        /** errSecNotAvailable. */
        const val NOT_AVAILABLE = "-25291"
    }

    private val defaults = NSUserDefaults.standardUserDefaults
    private val owned = "eg.bahr.session.test.keychainOwned"

    private fun store() = KeychainSessionStore(service = "eg.bahr.session.test", defaults = defaults, ownedKey = owned)

    private val session =
        StoredSession(
            tokens = AuthTokens("a1", "2026-10-14T10:15:00+03:00", "r1", "2026-11-13T10:00:00+03:00"),
            accountName = "Sara",
            profileComplete = true,
            providers = listOf("apple"),
        )

    @AfterTest
    fun tearDown() {
        defaults.removeObjectForKey(owned)
    }

    /** False in a process with no Keychain (see the class doc). */
    private suspend fun keychainAvailable(): Boolean =
        try {
            store().write(null)
            true
        } catch (e: IllegalStateException) {
            if (e.message?.contains(NOT_AVAILABLE) != true) throw e
            println("KeychainSessionStoreTest: no Keychain in this process (OSStatus $NOT_AVAILABLE); skipped")
            false
        }

    @Test
    fun `write - read - forget - read`() =
        runTest {
            if (!keychainAvailable()) return@runTest
            store().read() // this "install" now owns the item
            store().write(session)

            assertEquals(session, store().read(), "a new store instance reads what the last one wrote (a relaunch)")

            store().write(null)
            assertNull(store().read())
        }

    @Test
    fun `an item left by a previous install is cleared on the first read`() =
        runTest {
            if (!keychainAvailable()) return@runTest
            store().write(session)
            // The app was deleted and installed again: the Keychain kept the item, the preferences did not.
            defaults.removeObjectForKey(owned)

            assertNull(store().read(), "a fresh install starts signed out")
            store().write(session)
            assertEquals(session, store().read(), "and keeps what it writes itself")
        }
}
