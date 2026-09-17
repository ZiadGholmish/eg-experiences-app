package com.egyptexperiences.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.egyptexperiences.core.common.locale.AppLanguage
import com.egyptexperiences.core.datastore.AppSettingsStore
import com.egyptexperiences.core.datastore.createPreferencesDataStore
import com.egyptexperiences.core.datastore.di.dataStoreModule
import com.egyptexperiences.core.network.ApiConfig
import com.egyptexperiences.core.network.LanguageTagProvider
import com.egyptexperiences.core.network.di.networkModule
import com.egyptexperiences.feature.booking.di.bookingModule
import com.egyptexperiences.feature.trips.di.tripsModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Bindings only the app module can make: the API base URL lives in the build
 * config, and the preferences file path is platform-specific.
 */
fun appModule(
    baseUrl: String,
    isDebug: Boolean,
    preferencesPath: () -> String,
) = module {
    single { ApiConfig(baseUrl = baseUrl, isDebug = isDebug) }
    single<DataStore<Preferences>> { createPreferencesDataStore(preferencesPath) }

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

fun initKoin(
    baseUrl: String,
    isDebug: Boolean,
    preferencesPath: () -> String,
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication =
    startKoin {
        appDeclaration()
        modules(
            appModule(baseUrl, isDebug, preferencesPath),
            networkModule,
            dataStoreModule,
            tripsModule,
            bookingModule,
        )
    }
