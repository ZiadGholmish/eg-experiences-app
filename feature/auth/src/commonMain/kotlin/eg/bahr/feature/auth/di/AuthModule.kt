package eg.bahr.feature.auth.di

import eg.bahr.feature.auth.data.AuthApiService
import eg.bahr.feature.auth.data.AuthRepository
import eg.bahr.feature.auth.data.DefaultAuthRepository
import eg.bahr.feature.auth.presentation.DeleteAccountViewModel
import eg.bahr.feature.auth.presentation.SignInViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Sign-in and the account (M3-M0). `AuthSession` and `DeviceIdSource` come from the core modules;
 * [AuthConfig] (and on iOS the Swift `NativeSignIn`) from the app module.
 */
val authModule =
    module {
        single { AuthApiService(get()) }
        single<AuthRepository> { DefaultAuthRepository(get(), get()) }
        viewModel { SignInViewModel(get()) }
        viewModel { DeleteAccountViewModel(get()) }
    }
