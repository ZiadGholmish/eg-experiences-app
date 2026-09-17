package com.egyptexperiences.core.network.di

import com.egyptexperiences.core.network.ApiConfig
import com.egyptexperiences.core.network.HttpClientFactory
import com.egyptexperiences.core.network.LanguageTagProvider
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * [ApiConfig] and [LanguageTagProvider] are not declared here — the app module
 * supplies both, because only it can read `BuildConfig` and the stored locale.
 */
val networkModule =
    module {
        single<HttpClient> {
            HttpClientFactory.create(
                config = get<ApiConfig>(),
                languageTagProvider = get<LanguageTagProvider>(),
            )
        }
    }
