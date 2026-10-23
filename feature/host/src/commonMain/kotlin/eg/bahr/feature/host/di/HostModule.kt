package eg.bahr.feature.host.di

import eg.bahr.feature.host.data.DefaultHostRepository
import eg.bahr.feature.host.data.HostApiService
import eg.bahr.feature.host.data.HostRepository
import eg.bahr.feature.host.presentation.HostProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * A host's page. `GET /trips` and the waiting-list memory come from core:catalog's `catalogModule` (M4-M1c), which
 * the app starts alongside this one.
 */
val hostModule =
    module {
        single { HostApiService(get()) }
        single<HostRepository> { DefaultHostRepository(get(), get()) }
        viewModel { (slug: String, name: String?, avatar: String?) -> HostProfileViewModel(slug, name, avatar, get(), get()) }
    }
