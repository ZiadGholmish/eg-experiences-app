package eg.bahr.core.network.di

import eg.bahr.core.common.auth.DeviceIdRenewal
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.push.NoPushChannel
import eg.bahr.core.common.push.PushChannel
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.HttpClientFactory
import eg.bahr.core.network.LanguageTagProvider
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.HttpSessionRemote
import eg.bahr.core.network.install.InstallRegistrar
import eg.bahr.core.network.install.InstallSecrets
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
        // The install secret over core:datastore's secure store (bound there; a graph without one keeps it in memory),
        // shared by the client and the registration so both tell a failed read from none kept (M3-M5 review #1).
        single { InstallSecrets(getOrNull<InstallSecretStore>()) }
        single<HttpClient> {
            val config = get<ApiConfig>()
            val language = get<LanguageTagProvider>()
            val session = get<AuthSession>()
            val deviceIds = get<DeviceIdSource>()
            // The install secret for sign-in, holds and the guest inbox (M3-M5): the same reader as the registration's.
            val installSecrets = get<InstallSecrets>()
            // An engine bound in Koin is a test's MockEngine; the app binds none and Ktor picks the platform's.
            when (val engine = getOrNull<HttpClientEngine>()) {
                null -> HttpClientFactory.create(config, language, session, deviceIds, installSecrets)
                else -> HttpClientFactory.create(engine, config, language, session, deviceIds, installSecrets)
            }
        }
        // This install's registration (`registerDevice`, M3-M5), started by the app at launch. The push channel is the
        // platform's (bound by :composeApp); a graph without one registers with no token. The client is resolved on the
        // first registration, as the session's is.
        single {
            val scope = this
            InstallRegistrar(
                clientProvider = { scope.get<HttpClient>() },
                deviceIds = get(),
                renewal = get<DeviceIdRenewal>(),
                secrets = get<InstallSecrets>(),
                push = getOrNull<PushChannel>() ?: NoPushChannel,
                session = get(),
                language = get(),
            )
        }
    }
