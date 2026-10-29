package eg.bahr.core.network

import co.touchlab.kermit.Logger
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.HttpSessionRemote
import eg.bahr.core.network.auth.bahrAuth
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the one [HttpClient] the app uses.
 *
 * No engine is named: Ktor picks the engine from the classpath, which is OkHttp
 * in `androidMain` and Darwin in `iosMain` of the app module. Keeping the engine
 * out of here is what lets `core:network` stay platform-agnostic.
 */
internal object HttpClientFactory {
    /**
     * `ignoreUnknownKeys` is on because the contract serves two clients: the web
     * client can gain a field without this app shipping a release to tolerate it.
     */
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

    /**
     * The app's client, signed in through [session] (bearer token, refresh, `X-Device-Id`). The session
     * reaches the server through its own lazy link to this client (see `networkModule`), so sign-out and
     * refresh never depend on whether this client has been built yet (M3-M0 review #1).
     */
    fun create(
        config: ApiConfig,
        languageTagProvider: LanguageTagProvider,
        session: AuthSession,
        deviceIds: DeviceIdSource,
        installSecrets: InstallSecretStore?,
    ): HttpClient = HttpClient { configure(config, languageTagProvider, session, deviceIds, installSecrets) }

    fun create(
        engine: HttpClientEngine,
        config: ApiConfig,
        languageTagProvider: LanguageTagProvider,
        session: AuthSession?,
        deviceIds: DeviceIdSource?,
        installSecrets: InstallSecretStore? = null,
    ): HttpClient =
        HttpClient(engine) { configure(config, languageTagProvider, session, deviceIds, installSecrets) }
            // Tests build the client directly; it is the session's way to the server then.
            .also { client -> session?.remote = HttpSessionRemote { client } }

    private fun HttpClientConfig<*>.configure(
        config: ApiConfig,
        languageTagProvider: LanguageTagProvider,
        session: AuthSession?,
        deviceIds: DeviceIdSource?,
        installSecrets: InstallSecretStore?,
    ) {
        expectSuccess = false

        // Signed-in requests (M3-M0). Without a session (most ApiService tests) requests go anonymous.
        if (session != null) install(bahrAuth(session, deviceIds ?: DeviceIdSource { error("no device id source") }, installSecrets))

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = NetworkConstants.REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = NetworkConstants.CONNECT_TIMEOUT_MILLIS
        }

        if (config.isDebug) {
            install(Logging) {
                level = LogLevel.INFO
                logger =
                    object : io.ktor.client.plugins.logging.Logger {
                        override fun log(message: String) {
                            Logger.withTag("http").d(message)
                        }
                    }
            }
        }

        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
            header(
                NetworkConstants.HEADER_ACCEPT_LANGUAGE,
                languageTagProvider.currentLanguageTag(),
            )
        }
    }
}

/**
 * The app's real client (contract JSON settings, base URL, `Accept-Language`) on a given engine.
 *
 * For feature tests: an ApiService test runs against a Ktor `MockEngine` through this, so it decodes
 * with exactly the production `Json` (unknown keys ignored, absent nulls allowed) and sends the
 * production headers (and, given a [session], the production sign-in plugin), instead of a hand-copied configuration that could drift.
 */
fun apiHttpClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    session: AuthSession? = null,
    deviceIds: DeviceIdSource? = null,
    installSecrets: InstallSecretStore? = null,
    // Last, so the tests' trailing `{ "ar" }` still means the language.
    languageTagProvider: LanguageTagProvider,
): HttpClient = HttpClientFactory.create(engine, config, languageTagProvider, session, deviceIds, installSecrets)
