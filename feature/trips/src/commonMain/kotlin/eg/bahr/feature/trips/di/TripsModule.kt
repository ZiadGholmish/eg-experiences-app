package eg.bahr.feature.trips.di

import eg.bahr.feature.trips.data.DefaultTripRepository
import eg.bahr.feature.trips.data.TripApiService
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.presentation.CategoryTripsViewModel
import eg.bahr.feature.trips.presentation.SearchTripsViewModel
import eg.bahr.feature.trips.presentation.SectionTripsViewModel
import eg.bahr.feature.trips.presentation.TripDetailViewModel
import eg.bahr.feature.trips.presentation.TripListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tripsModule =
    module {
        single { TripApiService(get()) }
        single<TripRepository> { DefaultTripRepository(get()) }
        // Over core:datastore's WaitlistJoinsStore (M4-M5), from the module the app starts alongside this one.
        single { WaitlistMemory(get()) }
        viewModel { TripListViewModel(get(), get()) }
        viewModel { (slug: String) -> TripDetailViewModel(slug, get(), get()) }
        viewModel { (category: String, title: String?) -> CategoryTripsViewModel(category, title, get(), get()) }
        viewModel { (sectionId: String, title: String?) -> SectionTripsViewModel(sectionId, title, get(), get()) }
        // Recent searches come from core:datastore's module, which the app starts alongside this one.
        viewModel { SearchTripsViewModel(get(), get(), get()) }
    }
