package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import kotlinx.serialization.Serializable
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

/**
 * The install secret (`registerDevice`'s `installSecret`, M3-M5) in the same secure storage as the session, as its
 * own item: Keychain on iOS, a Keystore key of its own on Android.
 */
expect fun secureInstallSecretStore(dataStore: DataStore<Preferences>): InstallSecretStore

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

/**
 * The install secret with the device id it was issued to, as JSON. Read back only for that id: on iOS the Keychain
 * outlives an uninstall while the id (preferences) does not, and a secret sent with the wrong id is refused.
 */
internal object InstallSecretCodec {
    @Serializable
    private data class Stored(
        val deviceId: String,
        val secret: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(
        deviceId: String,
        secret: String,
    ): String = json.encodeToString(Stored.serializer(), Stored(deviceId, secret))

    /** The secret kept for [deviceId], or null when [raw] is absent, unreadable or for another id. */
    fun secretFor(
        deviceId: String,
        raw: String?,
    ): String? {
        val stored =
            raw?.let {
                try {
                    json.decodeFromString(Stored.serializer(), it)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        return stored?.takeIf { it.deviceId == deviceId }?.secret
    }
}
