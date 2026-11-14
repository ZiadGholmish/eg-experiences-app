package eg.bahr.feature.hostworkspace.di

import eg.bahr.core.network.auth.HostHttpClient
import eg.bahr.feature.hostworkspace.data.DefaultHostWorkspaceRepository
import eg.bahr.feature.hostworkspace.data.HostWorkspaceApiService
import eg.bahr.feature.hostworkspace.data.HostWorkspaceRepository
import eg.bahr.feature.hostworkspace.presentation.HostDepartureViewModel
import eg.bahr.feature.hostworkspace.presentation.HostHomeViewModel
import eg.bahr.feature.hostworkspace.presentation.HostSignInViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Host mode (M5-M1). `HostSession` and `HostHttpClient` come from core:network's module: host mode's own sign-in and
 * client, so nothing here ever sends the traveller's token (the unqualified `HttpClient` is the traveller's).
 */
val hostWorkspaceModule =
    module {
        single { HostWorkspaceApiService(get<HostHttpClient>().client) }
        single<HostWorkspaceRepository> { DefaultHostWorkspaceRepository(get(), get()) }
        viewModel { HostSignInViewModel(get()) }
        viewModel { HostHomeViewModel(get()) }
        viewModel { (departureId: String) -> HostDepartureViewModel(departureId, get()) }
    }
