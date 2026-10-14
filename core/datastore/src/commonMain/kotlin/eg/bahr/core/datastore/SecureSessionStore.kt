package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The sign-in (tokens + the account summary, M3-M0) in the platform's secure storage: the iOS
 * Keychain, or on Android a Keystore AES-GCM key whose ciphertext sits in the preferences file. Never
 * the plain preferences file: a refresh token is a credential for the whole account.
 *
 * No AndroidX/Google library does this for iOS (androidx.security's EncryptedSharedPreferences is
 * Android-only and deprecated), so it is written per platform on the system APIs, with no dependency.
 */
expect fun secureSessionStore(dataStore: DataStore<Preferences>): SessionStore

/** JSON for the stored session; a payload that no longer parses reads as signed out, never a crash. */
internal object SessionCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(session: StoredSession): String = json.encodeToString(StoredSession.serializer(), session)

    fun decode(raw: String?): StoredSession? =
        raw?.let {
            try {
                json.decodeFromString(StoredSession.serializer(), it)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
}
