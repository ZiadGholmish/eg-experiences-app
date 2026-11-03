package eg.bahr.feature.reviews.di

import eg.bahr.feature.reviews.data.DefaultReviewsRepository
import eg.bahr.feature.reviews.data.DefaultWriteReviewRepository
import eg.bahr.feature.reviews.data.ReviewsApiService
import eg.bahr.feature.reviews.data.ReviewsRepository
import eg.bahr.feature.reviews.data.WriteReviewApiService
import eg.bahr.feature.reviews.data.WriteReviewRepository
import eg.bahr.feature.reviews.presentation.ReviewsViewModel
import eg.bahr.feature.reviews.presentation.WriteReviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * "See all reviews". `GET /trips` (a host's trips, for the Trip filter) comes from core:catalog's `catalogModule`
 * (M4-M1c), which the app starts alongside this one. Since M3-M4 also the review page (`submitReview`).
 */
val reviewsModule =
    module {
        single { ReviewsApiService(get()) }
        single<ReviewsRepository> { DefaultReviewsRepository(get(), get()) }
        viewModel { (trip: String?, host: String?, title: String?) -> ReviewsViewModel(trip, host, title, get()) }
        single { WriteReviewApiService(get()) }
        single<WriteReviewRepository> { DefaultWriteReviewRepository(get()) }
        viewModel { (ref: String, title: String?) -> WriteReviewViewModel(ref, title, get()) }
    }
