package eg.bahr.feature.trips.di

import eg.bahr.feature.trips.data.DefaultTripRepository
import eg.bahr.feature.trips.data.TripApiService
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.presentation.CategoryTripsViewModel
import eg.bahr.feature.trips.presentation.HostProfileViewModel
import eg.bahr.feature.trips.presentation.SearchTripsViewModel
import eg.bahr.feature.trips.presentation.SectionTripsViewModel
import eg.bahr.feature.trips.presentation.TripDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The trip page, category page, "See all", search and the host page. `GET /trips`, the card cache and the
 * waiting-list memory come from core:catalog's `catalogModule` (shared with Home, M4-M1c), and the
 * recent searches from core:datastore's module; the app starts both alongside this one.
 */
val tripsModule =
    module {
        single { TripApiService(get()) }
        single<TripRepository> { DefaultTripRepository(get(), get(), get()) }
        viewModel { (slug: String) -> TripDetailViewModel(slug, get(), get()) }
        viewModel { (category: String, title: String?) -> CategoryTripsViewModel(category, title, get(), get()) }
        viewModel { (sectionId: String, title: String?) -> SectionTripsViewModel(sectionId, title, get(), get()) }
        viewModel { SearchTripsViewModel(get(), get(), get()) }
        viewModel { (slug: String, name: String?, avatar: String?) -> HostProfileViewModel(slug, name, avatar, get(), get()) }
    }
