package eg.bahr.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual fun secureSessionStore(dataStore: DataStore<Preferences>): SessionStore = KeystoreSessionStore(dataStore)

actual fun secureInstallSecretStore(dataStore: DataStore<Preferences>): InstallSecretStore = KeystoreInstallSecretStore(dataStore)

/**
 * The session JSON encrypted with an AES-256-GCM key that never leaves the Android Keystore; the
 * preferences file holds only `base64(iv) + "." + base64(ciphertext)`. A value that does not decrypt
 * (the key was wiped, e.g. after a restore onto a new device) reads as signed out, and is dropped.
 */
internal class KeystoreSessionStore(
    dataStore: DataStore<Preferences>,
) : SessionStore {
    private val sealed = SealedPreference(dataStore, stringPreferencesKey("session_sealed"), KeystoreSealer(KEY_ALIAS))

    override suspend fun read(): StoredSession? = SessionCodec.decode(sealed.read())

    override suspend fun write(session: StoredSession?) = sealed.write(session?.let(SessionCodec::encode))

    private companion object {
        const val KEY_ALIAS = "bahr_session"
    }
}

/**
 * The install secret (M3-M5) sealed the same way as the session, under a key of its own: a lost key then costs the
 * install its secret (it registers again under a new id), never the sign-in.
 */
internal class KeystoreInstallSecretStore(
    dataStore: DataStore<Preferences>,
) : InstallSecretStore {
    private val sealed = SealedPreference(dataStore, stringPreferencesKey("install_secret_sealed"), KeystoreSealer(KEY_ALIAS))

    override suspend fun read(deviceId: String): String? = InstallSecretCodec.secretFor(deviceId, sealed.read())

    override suspend fun write(
        deviceId: String,
        secret: String?,
    ) = sealed.write(secret?.let { InstallSecretCodec.encode(deviceId, it) })

    private companion object {
        const val KEY_ALIAS = "bahr_install"
    }
}

/** One preference holding a value sealed by [sealer]; a value that no longer opens reads as none and is dropped. */
private class SealedPreference(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String>,
    private val sealer: KeystoreSealer,
) {
    suspend fun read(): String? {
        val stored = dataStore.data.first()[key] ?: return null
        val plain = sealer.open(stored)
        if (plain == null) write(null)
        return plain
    }

    suspend fun write(plain: String?) {
        val value = plain?.let(sealer::seal)
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(key) else preferences[key] = value
        }
    }
}

/** AES-256-GCM under the Keystore key [alias] (made on first use): `base64(iv) + "." + base64(ciphertext)`. */
private class KeystoreSealer(
    private val alias: String,
) {
    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.doFinal(plain.encodeToByteArray())
        return encode(cipher.iv) + SEPARATOR + encode(sealed)
    }

    fun open(stored: String): String? =
        try {
            val (iv, sealed) = stored.split(SEPARATOR).takeIf { it.size == 2 } ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, decode(iv)))
            cipher.doFinal(decode(sealed)).decodeToString()
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SEPARATOR = "."
    }
}
