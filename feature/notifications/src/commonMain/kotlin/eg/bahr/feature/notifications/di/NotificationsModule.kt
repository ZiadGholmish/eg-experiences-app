package eg.bahr.feature.notifications.di

import eg.bahr.core.common.push.NoPushChannel
import eg.bahr.core.network.install.InstallRegistrar
import eg.bahr.feature.notifications.data.AlertsCenter
import eg.bahr.feature.notifications.data.DefaultNotificationsRepository
import eg.bahr.feature.notifications.data.NotificationsApiService
import eg.bahr.feature.notifications.data.NotificationsRepository
import eg.bahr.feature.notifications.navigation.UnreadAlerts
import eg.bahr.feature.notifications.presentation.NotificationsViewModel
import eg.bahr.feature.notifications.presentation.PushPromptViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The notifications page (M3-M6; pushed from Home's bell since M3-M6a), the bell's count ([UnreadAlerts], one per app,
 * on a scope of its own that outlives the page) and the push pre-prompt (M3-M5). `AuthSession`, the client and the
 * install's registration come from core:network, the pre-prompt's memory from core:datastore, and the platform's
 * `PushChannel` from :composeApp (a graph without one never asks: push is off).
 */
val notificationsModule =
    module {
        single { NotificationsApiService(get()) }
        single<NotificationsRepository> {
            val registrar = get<InstallRegistrar>()
            DefaultNotificationsRepository(api = get(), authSession = get(), register = registrar::register)
        }
        single {
            AlertsCenter(
                repository = get(),
                registrations = get<InstallRegistrar>().registrations,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        } bind UnreadAlerts::class
        viewModel { NotificationsViewModel(repository = get(), center = get(), errors = get()) }
        viewModel { PushPromptViewModel(push = getOrNull() ?: NoPushChannel, store = get()) }
    }
