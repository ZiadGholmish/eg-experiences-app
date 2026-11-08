package eg.bahr.feature.booking.di

import eg.bahr.feature.booking.data.BookingApiService
import eg.bahr.feature.booking.data.BookingRepository
import eg.bahr.feature.booking.data.DefaultBookingRepository
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.BookingConfirmedViewModel
import eg.bahr.feature.booking.presentation.BookingViewModel
import eg.bahr.feature.booking.presentation.ContinueBookingViewModel
import eg.bahr.feature.booking.presentation.HoldReminderPlanner
import eg.bahr.feature.booking.presentation.HoldReminderSchedule
import eg.bahr.feature.booking.presentation.HoldViewModel
import eg.bahr.feature.booking.presentation.OpenHoldViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookingModule =
    module {
        single { BookingApiService(get()) }
        single<BookingRepository> { DefaultBookingRepository(get(), get()) }
        // The hold reminder (M3-M7): one per app, so the placement, the held seats and Home's card share what was
        // scheduled. The platform's scheduler comes from :composeApp's entry points; a graph without one (tests)
        // schedules nothing.
        single<HoldReminderSchedule> { HoldReminderPlanner(reminders = getOrNull<HoldReminders>() ?: NoHoldReminders) }
        viewModel { (slug: String, departureId: String) ->
            BookingViewModel(slug, departureId, get(), get(), reminders = get())
        }
        // Checkout (M3-M1): the return link comes from :composeApp's entry points; without one none is sent.
        viewModel { (hold: HoldRoute) ->
            HoldViewModel(hold, get(), get(), reminders = get(), checkout = getOrNull<CheckoutConfig>() ?: CheckoutConfig())
        }
        viewModel { ContinueBookingViewModel(get(), get(), reminders = get()) }
        viewModel { (ref: String) -> BookingConfirmedViewModel(ref, get()) }
        viewModel { (ref: String) -> OpenHoldViewModel(ref, get(), get(), errors = get(), reminders = get()) }
    }
