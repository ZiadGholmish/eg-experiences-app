package eg.bahr.feature.tripdetails.di

import eg.bahr.feature.tripdetails.data.DefaultTripDetailRepository
import eg.bahr.feature.tripdetails.data.TripDetailApiService
import eg.bahr.feature.tripdetails.data.TripDetailRepository
import eg.bahr.feature.tripdetails.presentation.TripDetailViewModel
import eg.bahr.feature.tripdetails.presentation.TripLink
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The trip page. The card cache and the waiting-list memory come from core:catalog's `catalogModule` (shared with
 * Home and the trip lists, M4-M1c), which the app starts alongside this one.
 */
val tripDetailsModule =
    module {
        single { TripDetailApiService(get()) }
        // AuthSession (core:network): a signed-in person's waiting-list email is prefilled (M3-M5).
        single<TripDetailRepository> { DefaultTripDetailRepository(get(), get(), get()) }
        viewModel { (slug: String, link: TripLink) -> TripDetailViewModel(slug, get(), get(), link) }
    }
