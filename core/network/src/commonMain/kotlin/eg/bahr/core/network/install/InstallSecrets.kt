package eg.bahr.core.network.install

import co.touchlab.kermit.Logger
import eg.bahr.core.common.auth.InstallSecretStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * The one way to this install's secret (M3-M5, M3-M6): what the registration ([InstallRegistrar]) and every request that
 * sends `X-Install-Secret` (`BahrAuth`: sign-in, holds, the guest inbox) read, over the platform's secure [store].
 *
 * It tells two things apart that a plain nullable read mixes up (M3-M5 review #1): **no secret kept** for this id
 * ([Read.None]), and **the store failed** ([Read.Failed]: an iOS Keychain read before the first unlock, a transient
 * Android Keystore error). After a failed read the registration must not start the install over under a new id: the
 * secret is most likely still there, and a new id loses the install's anonymous holds and waiting-list entries.
 *
 * It also remembers the last secret issued in this run ([keep]), with its id, and gives it when the store cannot (an
 * unsigned iOS simulator build has no Keychain). Shared by both readers, so the guest inbox proves the install there
 * too, not only the registration.
 */
internal class InstallSecrets(
    private val store: InstallSecretStore?,
) : InstallSecretStore {
    sealed interface Read {
        data class Kept(
            val secret: String,
        ) : Read

        data object None : Read

        data object Failed : Read
    }

    private var issued: Pair<String, String>? = null

    /** The secret kept for [deviceId], none, or a store that failed (and nothing issued in this run to stand in). */
    suspend fun readFor(deviceId: String): Read {
        val fromStore =
            try {
                store?.read(deviceId)?.let { Read.Kept(it) } ?: Read.None
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Install secret: could not read it" }
                Read.Failed
            }
        if (fromStore is Read.Kept) return fromStore
        return issued?.takeIf { it.first == deviceId }?.let { Read.Kept(it.second) } ?: fromStore
    }

    /** For `BahrAuth`: the secret, null when none is kept, and a throw when the store failed (it then sends none). */
    override suspend fun read(deviceId: String): String? =
        when (val read = readFor(deviceId)) {
            is Read.Kept -> read.secret
            Read.None -> null
            Read.Failed -> error("Install secret store failed")
        }

    /** Keeps (or, with null, forgets) [deviceId]'s secret: in memory for this run, and in the store when it can. */
    override suspend fun write(
        deviceId: String,
        secret: String?,
    ) {
        issued = secret?.let { deviceId to it }
        try {
            store?.write(deviceId, secret)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Install secret: could not ${if (secret == null) "forget" else "keep"} it" }
        }
    }

    private companion object {
        val log = Logger.withTag("InstallSecrets")
    }
}
