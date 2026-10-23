package eg.bahr.feature.search.di

import eg.bahr.feature.search.data.LastSearch
import eg.bahr.feature.search.presentation.SearchTripsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Search. `GET /trips` (core:catalog's `TripListRepository`), the card cache and the waiting-list memory come from
 * core:catalog's `catalogModule` (shared with Home, M4-M1c), and the recent searches from core:datastore's module;
 * the app starts both alongside this one.
 */
val searchModule =
    module {
        // One last search for the run of the app (M3-M0b): reopening search shows it again.
        single { LastSearch() }
        viewModel { SearchTripsViewModel(get(), get(), get(), get()) }
    }
