package eg.bahr.feature.settings.di

import eg.bahr.feature.settings.data.DefaultSettingsRepository
import eg.bahr.feature.settings.data.ProfileApiService
import eg.bahr.feature.settings.data.SettingsRepository
import eg.bahr.feature.settings.presentation.EditPersonalInfoViewModel
import eg.bahr.feature.settings.presentation.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Profile (the Settings tab before M3-M0b) and Edit personal info. `AppSettingsStore`, `AuthSession`,
 * the `HttpClient` and `AppErrorController` come from the core and app modules.
 */
val settingsModule =
    module {
        single { ProfileApiService(get()) }
        single<SettingsRepository> { DefaultSettingsRepository(get(), get(), get()) }
        viewModel { ProfileViewModel(get(), get()) }
        viewModel { EditPersonalInfoViewModel(get()) }
    }
