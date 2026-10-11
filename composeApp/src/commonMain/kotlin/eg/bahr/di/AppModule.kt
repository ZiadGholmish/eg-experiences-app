package eg.bahr.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eg.bahr.core.catalog.di.catalogModule
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.datastore.createPreferencesDataStore
import eg.bahr.core.datastore.di.dataStoreModule
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.LanguageTagProvider
import eg.bahr.core.network.di.networkModule
import eg.bahr.deeplink.DeepLinkParser
import eg.bahr.feature.booking.di.bookingModule
import eg.bahr.feature.home.di.homeModule
import eg.bahr.feature.map.di.mapModule
import eg.bahr.feature.trips.di.tripsModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Where share links point, per environment: the host, and whether plain http is accepted (the local
 * flavor only, whose api serves its share page over http).
 */
data class AppLinkConfig(
    val host: String,
    val allowsHttp: Boolean,
)

/**
 * Bindings only the app module can make: the API base URL and the share-link host live in the build
 * config, and the preferences file path is platform-specific.
 */
fun appModule(
    baseUrl: String,
    isDebug: Boolean,
    appLinks: AppLinkConfig,
    preferencesPath: () -> String,
) = module {
    single { ApiConfig(baseUrl = baseUrl, isDebug = isDebug) }
    single { DeepLinkParser(host = appLinks.host, allowsHttp = appLinks.allowsHttp) }
    single<DataStore<Preferences>> { createPreferencesDataStore(preferencesPath) }

    // One per app: view models report transient errors here, App's BahrErrorHost shows them.
    single { AppErrorController() }

    /**
     * Reads the stored language on each request rather than caching it, so a
     * language switch changes the very next call's `Accept-Language`.
     *
     * `runBlocking` on a DataStore read is acceptable here and nowhere else:
     * it happens on Ktor's request pipeline, the value is already in memory
     * after the first read, and the alternative — a suspending header provider
     * — does not exist in Ktor's `defaultRequest`.
     */
    single<LanguageTagProvider> {
        val settings: AppSettingsStore = get()
        LanguageTagProvider {
            runBlocking { runCatching { settings.language.first() }.getOrDefault(AppLanguage.default) }.tag
        }
    }
}

/**
 * Every Koin module the app starts, in order. One list for [initKoin] and for the test that starts it
 * whole (M4-M7), so a type two modules both bind is caught before it reaches a device.
 */
fun appModules(
    baseUrl: String,
    isDebug: Boolean,
    appLinks: AppLinkConfig,
    preferencesPath: () -> String,
): List<Module> =
    listOf(
        appModule(baseUrl, isDebug, appLinks, preferencesPath),
        networkModule,
        dataStoreModule,
        // Shared by Home and the trip lists (M4-M1c): one card cache, one waiting-list memory.
        catalogModule,
        homeModule,
        tripsModule,
        bookingModule,
        mapModule,
    )

fun initKoin(
    baseUrl: String,
    isDebug: Boolean,
    appLinks: AppLinkConfig,
    preferencesPath: () -> String,
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication =
    startKoin {
        appDeclaration()
        modules(appModules(baseUrl, isDebug, appLinks, preferencesPath))
    }
