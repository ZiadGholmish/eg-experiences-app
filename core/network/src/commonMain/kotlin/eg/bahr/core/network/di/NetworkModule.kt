package eg.bahr.core.network.di

import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.HttpClientFactory
import eg.bahr.core.network.LanguageTagProvider
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.HttpSessionRemote
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module

/**
 * [ApiConfig] and [LanguageTagProvider] are not declared here — the app module
 * supplies both, because only it can read `BuildConfig` and the stored locale.
 */
val networkModule =
    module {
        // The device's sign-in (M3-M0). SessionStore and DeviceIdSource come from core:datastore's module.
        // Its way to the server resolves the client lazily, on the first refresh or sign-out, so a sign-out
        // before any other request (a relaunch straight onto Settings) still revokes the refresh token
        // (M3-M0 review #1). Lazy also breaks the cycle: the client's plugin needs the session.
        single {
            val scope = this
            AuthSession(store = get<SessionStore>()).also { session ->
                session.remote = HttpSessionRemote { scope.get<HttpClient>() }
            }
        }
        single<HttpClient> {
            val config = get<ApiConfig>()
            val language = get<LanguageTagProvider>()
            val session = get<AuthSession>()
            val deviceIds = get<DeviceIdSource>()
            // An engine bound in Koin is a test's MockEngine; the app binds none and Ktor picks the platform's.
            when (val engine = getOrNull<HttpClientEngine>()) {
                null -> HttpClientFactory.create(config, language, session, deviceIds)
                else -> HttpClientFactory.create(engine, config, language, session, deviceIds)
            }
        }
    }
