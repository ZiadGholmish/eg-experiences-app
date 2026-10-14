package eg.bahr.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

/**
 * The session JSON encrypted with an AES-256-GCM key that never leaves the Android Keystore; the
 * preferences file holds only `base64(iv) + "." + base64(ciphertext)`. A value that does not decrypt
 * (the key was wiped, e.g. after a restore onto a new device) reads as signed out, and is dropped.
 */
internal class KeystoreSessionStore(
    private val dataStore: DataStore<Preferences>,
) : SessionStore {
    override suspend fun read(): StoredSession? {
        val stored = dataStore.data.first()[key] ?: return null
        val plain = decrypt(stored)
        if (plain == null) {
            write(null)
            return null
        }
        return SessionCodec.decode(plain)
    }

    override suspend fun write(session: StoredSession?) {
        val value = session?.let { encrypt(SessionCodec.encode(it)) }
        dataStore.edit { preferences ->
            if (value == null) preferences.remove(key) else preferences[key] = value
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.doFinal(plain.encodeToByteArray())
        return encode(cipher.iv) + SEPARATOR + encode(sealed)
    }

    private fun decrypt(stored: String): String? =
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
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
        val key = stringPreferencesKey("session_sealed")
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bahr_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SEPARATOR = "."
    }
}
