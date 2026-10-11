package eg.bahr.feature.settings.di

import eg.bahr.feature.settings.data.DefaultSettingsRepository
import eg.bahr.feature.settings.data.SettingsRepository
import eg.bahr.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Settings. `AppSettingsStore` and `AppErrorController` come from the core and app modules. */
val settingsModule =
    module {
        single<SettingsRepository> { DefaultSettingsRepository(get()) }
        viewModel { SettingsViewModel(get(), get()) }
    }
