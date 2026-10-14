package eg.bahr.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** iOS keeps the session in the Keychain; the preferences file is not used. */
actual fun secureSessionStore(dataStore: DataStore<Preferences>): SessionStore = KeychainSessionStore()

/**
 * One generic-password item ([service], [ACCOUNT]) holding the session JSON, readable after the first
 * unlock and never synced or restored to another device (`…ThisDeviceOnly`): a copied refresh token
 * would be replayed from two devices, which revokes the sign-in (openapi `refreshTokens`).
 *
 * The Keychain outlives an uninstall, the preferences do not. So the first read of an install (no
 * [ownedKey] in [defaults] yet) deletes whatever item a previous install left, and an install that
 * starts signed out stays signed out, as on Android (M3-M0 review #7).
 *
 * A failed Keychain call throws with its `OSStatus`; `AuthSession` logs it (and, on a read, starts
 * signed out) instead of the failure passing silently (M3-M0 review #4). [service] is a parameter only
 * so the simulator test can use an item of its own.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KeychainSessionStore(
    private val service: String = SERVICE,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val ownedKey: String = OWNED_KEY,
) : SessionStore {
    override suspend fun read(): StoredSession? {
        if (!defaults.boolForKey(ownedKey)) {
            delete()
            defaults.setBool(true, forKey = ownedKey)
            return null
        }
        return memScoped {
            val query =
                query(
                    // CF's own boolean: a Kotlin `true` would cross the bridge as a KotlinBoolean.
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                )
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            CFBridgingRelease(query)
            when (status) {
                errSecSuccess -> Unit
                errSecItemNotFound -> return@memScoped null
                else -> error("Keychain read failed: OSStatus $status")
            }
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            SessionCodec.decode(NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString())
        }
    }

    override suspend fun write(session: StoredSession?) {
        // Delete then add: simpler than SecItemUpdate's two dictionaries, and a write is rare (sign-in,
        // a token refresh every so often).
        delete()
        defaults.setBool(true, forKey = ownedKey)
        if (session == null) return
        val data = (SessionCodec.encode(session) as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: error("Session not encodable")
        val add =
            query(
                kSecValueData to data,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
        val status = SecItemAdd(add, null)
        CFBridgingRelease(add)
        check(status == errSecSuccess) { "Keychain write failed: OSStatus $status" }
    }

    private fun delete() {
        val match = query()
        val status = SecItemDelete(match)
        CFBridgingRelease(match)
        check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain delete failed: OSStatus $status" }
    }

    /**
     * The item's identity plus [extra], as a retained CFDictionary the caller releases. Keys and values
     * that are CF constants are retained before bridging, so releasing the dictionary never
     * over-releases a system constant.
     */
    private fun query(vararg extra: Pair<Any?, Any?>): CFDictionaryRef? {
        val entries =
            listOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to ACCOUNT,
            ) + extra
        val dictionary = entries.associate { (key, value) -> bridged(key) to bridged(value) }
        @Suppress("UNCHECKED_CAST")
        return CFBridgingRetain(dictionary) as CFDictionaryRef?
    }

    private fun bridged(value: Any?): Any? =
        when (value) {
            is kotlinx.cinterop.CPointer<*> -> CFBridgingRelease(CFRetain(value))
            else -> value
        }

    internal companion object {
        const val SERVICE = "eg.bahr.session"
        const val ACCOUNT = "default"

        /** In the app's preferences (gone with an uninstall): this install owns the Keychain item. */
        const val OWNED_KEY = "eg.bahr.session.keychainOwned"
    }
}
