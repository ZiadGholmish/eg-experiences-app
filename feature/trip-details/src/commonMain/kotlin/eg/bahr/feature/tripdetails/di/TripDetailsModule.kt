package eg.bahr.feature.tripdetails.di

import eg.bahr.feature.tripdetails.data.DefaultTripDetailRepository
import eg.bahr.feature.tripdetails.data.TripDetailApiService
import eg.bahr.feature.tripdetails.data.TripDetailRepository
import eg.bahr.feature.tripdetails.presentation.TripDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The trip page. The card cache and the waiting-list memory come from core:catalog's `catalogModule` (shared with
 * Home and the trip lists, M4-M1c), which the app starts alongside this one.
 */
val tripDetailsModule =
    module {
        single { TripDetailApiService(get()) }
        single<TripDetailRepository> { DefaultTripDetailRepository(get(), get()) }
        viewModel { (slug: String) -> TripDetailViewModel(slug, get(), get()) }
    }
