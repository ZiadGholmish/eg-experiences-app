package com.egyptexperiences.feature.booking.di

import com.egyptexperiences.feature.booking.data.BookingApiService
import com.egyptexperiences.feature.booking.data.BookingRepository
import com.egyptexperiences.feature.booking.presentation.BookingConfirmedViewModel
import com.egyptexperiences.feature.booking.presentation.BookingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookingModule =
    module {
        single { BookingApiService(get()) }
        single { BookingRepository(get()) }
        viewModel { (departureId: Long) -> BookingViewModel(departureId, get(), get()) }
        viewModel { (ref: String) -> BookingConfirmedViewModel(ref, get()) }
    }
