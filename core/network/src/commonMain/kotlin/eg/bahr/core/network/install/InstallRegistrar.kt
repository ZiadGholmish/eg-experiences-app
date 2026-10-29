package eg.bahr.core.network.install

import co.touchlab.kermit.Logger
import eg.bahr.core.common.auth.DeviceIdRenewal
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.push.PushChannel
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.ApiErrorReasons
import eg.bahr.core.network.LanguageTagProvider
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.HEADER_DEVICE_ID
import eg.bahr.core.network.auth.HEADER_INSTALL_SECRET
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.network.callApi
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** openapi `DeviceRequest`. [token] is left out (not null) without push permission or FCM. */
@Serializable
internal data class DeviceRequestDto(
    val token: String? = null,
    val platform: String,
    val locale: String? = null,
)

/** openapi `DeviceRegistration`: [installSecret] only in the answer that issues one. */
@Serializable
internal data class DeviceRegistrationDto(
    val installSecret: String? = null,
)

/**
 * This install's registration with the server (openapi `registerDevice`, M3-B8/B12; M3-M5): on every start, when the
 * FCM token or the notification permission changes, and on each sign-in and sign-out (a registration with the bearer
 * token links the install to the account and moves its guest inbox there; one without unlinks it).
 *
 * The contract's rules for the install secret:
 * - the first registration answers `installSecret`; it is kept in secure storage ([secrets]) with the id it was
 *   issued to, and sent as `X-Install-Secret` on every later registration (and, through `withInstallSecret()`, on
 *   sign-in and the guest inbox);
 * - **one registration at a time** ([mutex]): two first registrations at once give the secret to one and
 *   FORBIDDEN to the other;
 * - FORBIDDEN with reason `INSTALL_SECRET_INVALID` means this id's secret is gone or wrong for good: the install
 *   starts over under a new id ([renewal]) and registers once more without a secret. Never more than once per call,
 *   never on any other failure (a network error or a 429 is retried at the next trigger), and never a sign-out: the
 *   tokens are not the install's.
 *
 * The client is resolved on first use, as `AuthSession`'s remote is: building this must not build the client.
 */
class InstallRegistrar internal constructor(
    private val clientProvider: () -> HttpClient,
    private val deviceIds: DeviceIdSource,
    private val renewal: DeviceIdRenewal,
    private val secrets: InstallSecretStore?,
    private val push: PushChannel,
    private val session: AuthSession,
    private val language: LanguageTagProvider,
    private val platform: String = devicePlatform,
    private val coalesce: Duration = COALESCE,
) {
    private val client: HttpClient by lazy(clientProvider)
    private val mutex = Mutex()

    /**
     * The last secret issued in this run, with its id: what is sent when secure storage cannot give it back (an
     * unsigned iOS simulator build has no Keychain), so this run's later registrations still prove the install.
     */
    private var issued: Pair<String, String>? = null

    /**
     * Registers on every change of what the server should know: signed in or out (once the stored sign-in has been
     * read), and the token. The first value is the start-up registration. Changes close together (the token arriving
     * just after the sign-in was read) are coalesced into one.
     */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job =
        scope.launch {
            launch { session.load() }
            val signedIn =
                session.state
                    .filter { it != SessionState.Unknown }
                    .map { it is SessionState.SignedIn }
                    .distinctUntilChanged()
            combine(signedIn, push.token) { inAccount, token -> inAccount to token }
                .distinctUntilChanged()
                .debounce(coalesce)
                .collect { register() }
        }

    /**
     * One registration, after any in flight has answered. Also what the guest inbox calls on `INSTALL_SECRET_INVALID`
     * (M3-M6): register again, which starts over under a new id if the secret is refused here too.
     */
    suspend fun register(): AppResult<Unit> =
        mutex.withLock {
            val deviceId = deviceIds.deviceId()
            when (val first = send(deviceId, secretFor(deviceId))) {
                is AppResult.Success -> first.keepSecret(deviceId)
                is AppResult.Failure ->
                    if (first.error.isSecretRefused()) {
                        log.w { "Install secret refused; registering under a new install id" }
                        forget(deviceId)
                        val fresh = renewal.renewDeviceId()
                        send(fresh, secret = null).keepSecret(fresh)
                    } else {
                        log.i { "Device registration failed (${first.error}); next trigger retries" }
                        first
                    }
            }
        }

    private suspend fun send(
        deviceId: String,
        secret: String?,
    ): AppResult<DeviceRegistrationDto> {
        val token = push.token.value
        val body = DeviceRequestDto(token = token, platform = platform, locale = language.currentLanguageTag())
        return callApi {
            client.post("devices") {
                header(HEADER_DEVICE_ID, deviceId)
                secret?.let { header(HEADER_INSTALL_SECRET, it) }
                setBody(body)
            }
        }
    }

    private suspend fun AppResult<DeviceRegistrationDto>.keepSecret(deviceId: String): AppResult<Unit> =
        when (this) {
            is AppResult.Success -> {
                data.installSecret?.let { secret ->
                    issued = deviceId to secret
                    guarded("keep") { secrets?.write(deviceId, secret) }
                }
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> this
        }

    private suspend fun secretFor(deviceId: String): String? =
        guarded("read") { secrets?.read(deviceId) } ?: issued?.takeIf { it.first == deviceId }?.second

    private suspend fun forget(deviceId: String) {
        issued = null
        guarded("forget") { secrets?.write(deviceId, null) }
    }

    /** Secure storage that fails is logged and treated as empty: the registration still goes. */
    private suspend fun <T> guarded(
        what: String,
        block: suspend () -> T?,
    ): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Install secret: could not $what it" }
            null
        }

    private fun AppError.isSecretRefused(): Boolean =
        this is AppError.Api && code == ApiErrorCodes.FORBIDDEN && reason == ApiErrorReasons.INSTALL_SECRET_INVALID

    internal companion object {
        /** Long enough to fold the token's arrival into the start-up registration, short enough to go unnoticed. */
        val COALESCE: Duration = 500.milliseconds

        private val log = Logger.withTag("InstallRegistrar")
    }
}

/** openapi `DeviceRequest.platform` for this build: `ANDROID` or `IOS`. */
internal expect val devicePlatform: String
