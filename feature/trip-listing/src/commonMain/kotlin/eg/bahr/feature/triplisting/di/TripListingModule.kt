package eg.bahr.feature.triplisting.di

import eg.bahr.feature.triplisting.data.DefaultTripListingRepository
import eg.bahr.feature.triplisting.data.TripListingApiService
import eg.bahr.feature.triplisting.data.TripListingRepository
import eg.bahr.feature.triplisting.presentation.CategoryTripsViewModel
import eg.bahr.feature.triplisting.presentation.SectionTripsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The category page and a Home row's "See all". `GET /trips`, the card cache and the waiting-list memory come from
 * core:catalog's `catalogModule` (shared with Home, M4-M1c), which the app starts alongside this one.
 */
val tripListingModule =
    module {
        single { TripListingApiService(get()) }
        single<TripListingRepository> { DefaultTripListingRepository(get(), get(), get()) }
        viewModel { (category: String, title: String?) -> CategoryTripsViewModel(category, title, get(), get()) }
        viewModel { (sectionId: String, title: String?) -> SectionTripsViewModel(sectionId, title, get(), get()) }
    }
