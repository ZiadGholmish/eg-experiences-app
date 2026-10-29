package eg.bahr.core.datastore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M3-M5: the install secret's codec, the device id's renewal and the push pre-prompt's memory. */
class InstallStorageTest {
    @Test
    fun `a secret reads back only for the id it was issued to`() {
        val stored = InstallSecretCodec.encode(ID, "s3cret")

        assertEquals("s3cret", InstallSecretCodec.secretFor(ID, stored))
        assertNull(InstallSecretCodec.secretFor(OTHER_ID, stored), "a Keychain item a previous install left")
        assertNull(InstallSecretCodec.secretFor(ID, null))
        assertNull(InstallSecretCodec.secretFor(ID, "not json"), "unreadable is none, never a crash")
    }

    @Test
    fun `renewing the device id replaces it for good`() =
        runTest {
            val preferences = InMemoryPreferences()
            val store = PreferencesDeviceIdStore(preferences)
            val first = store.deviceId()

            val renewed = store.renewDeviceId()

            assertNotEquals(first, renewed)
            assertEquals(renewed, store.deviceId())
            assertEquals(renewed, PreferencesDeviceIdStore(preferences).deviceId(), "kept in the file")
        }

    @Test
    fun `the pre-prompt counts each Not now and remembers the system dialog`() =
        runTest {
            val preferences = InMemoryPreferences()
            val store = PreferencesPushPromptStore(preferences)
            assertEquals(0, store.declines())
            assertFalse(store.systemDialogShown())

            store.recordDecline()
            store.recordDecline()
            store.recordSystemDialogShown()

            val reopened = PreferencesPushPromptStore(preferences)
            assertEquals(2, reopened.declines())
            assertTrue(reopened.systemDialogShown())
        }

    private companion object {
        const val ID = "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e"
        const val OTHER_ID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"
    }
}
