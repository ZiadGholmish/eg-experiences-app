package eg.bahr.feature.booking.di

import eg.bahr.feature.booking.data.BookingApiService
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.data.DefaultBookingRepository
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.BookingConfirmedViewModel
import eg.bahr.feature.booking.presentation.BookingViewModel
import eg.bahr.feature.booking.presentation.HoldViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookingModule =
    module {
        single { BookingApiService(get()) }
        single<BookingRepository> { DefaultBookingRepository(get()) }
        viewModel { (slug: String, departureId: String) -> BookingViewModel(slug, departureId, get()) }
        viewModel { (hold: HoldRoute) -> HoldViewModel(hold, get()) }
        viewModel { (ref: String) -> BookingConfirmedViewModel(ref, get()) }
    }
