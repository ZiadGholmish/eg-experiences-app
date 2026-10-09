package eg.bahr.core.network

import co.touchlab.kermit.Logger
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

    fun create(
        config: ApiConfig,
        languageTagProvider: LanguageTagProvider,
    ): HttpClient = HttpClient { configure(config, languageTagProvider) }

    fun create(
        engine: HttpClientEngine,
        config: ApiConfig,
        languageTagProvider: LanguageTagProvider,
    ): HttpClient = HttpClient(engine) { configure(config, languageTagProvider) }

    private fun HttpClientConfig<*>.configure(
        config: ApiConfig,
        languageTagProvider: LanguageTagProvider,
    ) {
        expectSuccess = false

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
 * production headers, instead of a hand-copied configuration that could drift.
 */
fun apiHttpClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    languageTagProvider: LanguageTagProvider,
): HttpClient = HttpClientFactory.create(engine, config, languageTagProvider)
