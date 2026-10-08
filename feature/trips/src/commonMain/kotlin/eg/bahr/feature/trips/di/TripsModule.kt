package eg.bahr.feature.trips.di

import eg.bahr.feature.trips.data.TripApiService
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.presentation.TripDetailViewModel
import eg.bahr.feature.trips.presentation.TripListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tripsModule =
    module {
        single { TripApiService(get()) }
        single { TripRepository(get()) }
        viewModel { TripListViewModel(get()) }
        viewModel { (slug: String) -> TripDetailViewModel(slug, get()) }
    }
