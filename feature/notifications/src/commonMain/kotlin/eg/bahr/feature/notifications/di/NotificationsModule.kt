package eg.bahr.feature.notifications.di

import eg.bahr.core.common.push.NoPushChannel
import eg.bahr.feature.notifications.data.DefaultNotificationsRepository
import eg.bahr.feature.notifications.data.NotificationsRepository
import eg.bahr.feature.notifications.presentation.NotificationsViewModel
import eg.bahr.feature.notifications.presentation.PushPromptViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The notifications page (M3-M0b; pushed from Home's bell since M3-M6a) and the push pre-prompt (M3-M5).
 * `AuthSession` comes from core:network, the pre-prompt's memory from core:datastore, and the platform's
 * `PushChannel` from :composeApp (a graph without one never asks: push is off).
 */
val notificationsModule =
    module {
        single<NotificationsRepository> { DefaultNotificationsRepository(get()) }
        viewModel { NotificationsViewModel(get()) }
        viewModel { PushPromptViewModel(push = getOrNull() ?: NoPushChannel, store = get()) }
    }
