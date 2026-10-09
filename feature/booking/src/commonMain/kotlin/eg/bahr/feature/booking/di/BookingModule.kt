package eg.bahr.feature.booking.di

import eg.bahr.feature.booking.data.BookingApiService
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.data.DefaultBookingRepository
import eg.bahr.feature.booking.presentation.BookingConfirmedViewModel
import eg.bahr.feature.booking.presentation.BookingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookingModule =
    module {
        single { BookingApiService(get()) }
        single<BookingRepository> { DefaultBookingRepository(get()) }
        viewModel { (slug: String, departureId: String) -> BookingViewModel(slug, departureId, get()) }
        viewModel { (ref: String) -> BookingConfirmedViewModel(ref, get()) }
    }
