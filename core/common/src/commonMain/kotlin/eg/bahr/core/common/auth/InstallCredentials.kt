package eg.bahr.core.common.auth

/**
 * The per-install secret `registerDevice` issues once (openapi `DeviceRegistration.installSecret`, M3-B12): sent as
 * `X-Install-Secret` on later registrations, the guest inbox and sign-in, so a bare `X-Device-Id` (which travels on
 * every request) proves nothing. Kept in the platform's secure storage (Keychain / Keystore-encrypted, core:datastore).
 *
 * Kept together with the [deviceId] it was issued to: the iOS Keychain outlives an uninstall while the device id
 * (preferences) does not, so a secret is only ever read back for the id it belongs to.
 */
interface InstallSecretStore {
    /** The secret issued to [deviceId], or null when none is kept for that id. */
    suspend fun read(deviceId: String): String?

    /** Keeps [secret] for [deviceId], replacing whatever was kept; null forgets it. */
    suspend fun write(
        deviceId: String,
        secret: String?,
    )
}

/**
 * Starts the install over under a new `X-Device-Id` (openapi `registerDevice`): what an app does when the server
 * refuses its install secret and it has none that works. Anonymous holds and waiting-list entries made under the old
 * id stay with the old id, so this is the last resort, never a reaction to a network error.
 */
fun interface DeviceIdRenewal {
    suspend fun renewDeviceId(): String
}
