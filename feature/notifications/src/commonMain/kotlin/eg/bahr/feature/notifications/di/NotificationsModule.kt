package eg.bahr.feature.notifications.di

import eg.bahr.feature.notifications.data.DefaultNotificationsRepository
import eg.bahr.feature.notifications.data.NotificationsRepository
import eg.bahr.feature.notifications.presentation.NotificationsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The Alerts tab (M3-M0b). `AuthSession` comes from core:network. */
val notificationsModule =
    module {
        single<NotificationsRepository> { DefaultNotificationsRepository(get()) }
        viewModel { NotificationsViewModel(get()) }
    }
