package com.egyptexperiences.feature.trips.di

import com.egyptexperiences.feature.trips.data.TripApiService
import com.egyptexperiences.feature.trips.data.TripRepository
import com.egyptexperiences.feature.trips.presentation.TripDetailViewModel
import com.egyptexperiences.feature.trips.presentation.TripListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tripsModule =
    module {
        single { TripApiService(get()) }
        single { TripRepository(get()) }
        viewModel { TripListViewModel(get()) }
        viewModel { (slug: String) -> TripDetailViewModel(slug, get()) }
    }
