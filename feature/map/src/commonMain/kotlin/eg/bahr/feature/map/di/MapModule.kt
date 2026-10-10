package eg.bahr.feature.map.di

import eg.bahr.feature.map.data.DefaultTripMapRepository
import eg.bahr.feature.map.data.TripMapApiService
import eg.bahr.feature.map.data.TripMapRepository
import eg.bahr.feature.map.presentation.TripMapViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mapModule =
    module {
        single { TripMapApiService(get()) }
        single<TripMapRepository> { DefaultTripMapRepository(get()) }
        viewModel { TripMapViewModel(get()) }
    }
