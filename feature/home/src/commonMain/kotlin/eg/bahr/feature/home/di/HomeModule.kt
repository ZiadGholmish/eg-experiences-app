package eg.bahr.feature.home.di

import eg.bahr.feature.home.data.DefaultHomeRepository
import eg.bahr.feature.home.data.HomeApiService
import eg.bahr.feature.home.data.HomeRepository
import eg.bahr.feature.home.presentation.TripListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Home. `GET /trips`, the card cache and the waiting-list memory come from core:catalog's
 * `catalogModule` (shared with the trip lists, M4-M1c), which the app starts alongside this one.
 */
val homeModule =
    module {
        single { HomeApiService(get()) }
        single<HomeRepository> { DefaultHomeRepository(get(), get(), get()) }
        viewModel { TripListViewModel(get(), get()) }
    }
