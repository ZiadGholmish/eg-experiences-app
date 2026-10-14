package eg.bahr.feature.mytrips.di

import eg.bahr.feature.mytrips.data.DefaultMyTripsRepository
import eg.bahr.feature.mytrips.data.MyTripsApiService
import eg.bahr.feature.mytrips.data.MyTripsRepository
import eg.bahr.feature.mytrips.presentation.MyTripsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** My trips. `AuthSession` from core:network; `WaitlistMemory` and `TripCardCache` from core:catalog. */
val myTripsModule =
    module {
        single { MyTripsApiService(get()) }
        single<MyTripsRepository> { DefaultMyTripsRepository(get(), get(), get(), get()) }
        viewModel { MyTripsViewModel(get()) }
    }
