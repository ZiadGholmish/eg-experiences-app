package eg.bahr.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.common.auth.DeviceIdRenewal
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.datastore.ActiveHoldStore
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.datastore.RecentSearchesStore
import eg.bahr.core.datastore.WaitlistJoinsStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.LanguageTagProvider
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.install.InstallRegistrar
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.deeplink.DeepLinkParser
import eg.bahr.feature.auth.di.AuthConfig
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.error.DefinitionOverrideException
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * The app's whole Koin graph, [appModules] as [initKoin] starts it (M4-M7, M4-M1c round 2 #2). The
 * feature modules' own graph tests start each feature alone; only here do they meet, so only here is a
 * type that two of them both bind caught. Overrides are refused ([runKoinGraphTest]), so such a type
 * fails the load instead of one binding silently replacing the other.
 *
 * Nothing is stubbed: the real HTTP client is built but never called, and the preferences file is
 * never read (DataStore opens it on the first read). The platform entry points add only what common
 * code cannot bind: Android its `androidContext`, iOS the map's native views.
 *
 * The view models and repositories behind each feature are `internal` to it, so they are resolved by
 * the features' own graph tests (`HomeKoinGraphTest`, `TripsKoinGraphTest`); this test resolves what
 * the app module and the core modules bind, which every feature's bindings stand on.
 */
class AppKoinGraphTest {
    private fun modules() =
        appModules(
            baseUrl = TEST_BASE_URL,
            isDebug = false,
            appLinks = AppLinkConfig(host = "bahr.eg", allowsHttp = false),
            auth = AuthConfig(googleServerClientId = "", allowFakeSignIn = false),
            // Never opened: nothing here reads the store.
            preferencesPath = { "app-koin-graph-test.preferences_pb" },
        )

    @Test
    fun `the full module list loads with no type bound twice - and its shared bindings resolve`() =
        runKoinGraphTest(modules()) { koin ->
            koin.resolvesAppBindings()
            assertSame(koin.get<TripCardCache>(), koin.get<TripCardCache>(), "one card cache for Home and the trip page")
            assertSame(koin.get<WaitlistMemory>(), koin.get<WaitlistMemory>(), "one waiting-list memory for every list")
            assertSame(koin.get<AuthSession>(), koin.get<AuthSession>(), "one sign-in for every request and screen (M3-M0)")
            assertSame(koin.get<InstallRegistrar>(), koin.get<InstallRegistrar>(), "one registration of the install at a time (M3-M5)")
            assertSame<Any>(koin.get<DeviceIdSource>(), koin.get<DeviceIdRenewal>(), "the id renewed is the id sent (M3-M5)")
            koin.get<InstallSecretStore>()
            koin.get<HttpClient>().close()
        }

    /** Proves the check above can fail: one more binding of a type the graph already has. */
    @Test
    fun `a type bound twice fails the load`() {
        assertFailsWith<DefinitionOverrideException> {
            runKoinGraphTest(modules() + module { single { TripCardCache() } }) {}
        }
    }

    private fun Koin.resolvesAppBindings() {
        get<ApiConfig>()
        get<DeepLinkParser>()
        get<DataStore<Preferences>>()
        get<AppErrorController>()
        get<LanguageTagProvider>()
        get<AppSettingsStore>()
        get<ActiveHoldStore>()
        get<RecentSearchesStore>()
        get<WaitlistJoinsStore>()
        get<TripListRepository>()
        // The sign-in (M3-M0): constructed only; nothing reads the secure store here.
        get<AuthConfig>()
        get<SessionStore>()
        get<DeviceIdSource>()
        get<AuthSession>()
    }
}
