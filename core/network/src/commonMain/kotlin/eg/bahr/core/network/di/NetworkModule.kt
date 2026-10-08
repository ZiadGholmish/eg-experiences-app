package eg.bahr.core.network.di

import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.HttpClientFactory
import eg.bahr.core.network.LanguageTagProvider
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
