package eg.bahr.core.datastore.di

import eg.bahr.core.common.auth.DeviceIdRenewal
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.datastore.ActiveHoldStore
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.datastore.PreferencesActiveHoldStore
import eg.bahr.core.datastore.PreferencesDeviceIdStore
import eg.bahr.core.datastore.PreferencesPushPromptStore
import eg.bahr.core.datastore.PreferencesRecentSearchesStore
import eg.bahr.core.datastore.PreferencesWaitlistJoinsStore
import eg.bahr.core.datastore.PushPromptStore
import eg.bahr.core.datastore.RecentSearchesStore
import eg.bahr.core.datastore.WaitlistJoinsStore
import eg.bahr.core.datastore.secureInstallSecretStore
import eg.bahr.core.datastore.secureSessionStore
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The `DataStore<Preferences>` itself is bound by the app module, which is the
 * only place that can produce a platform file path.
 */
val dataStoreModule =
    module {
        single { AppSettingsStore(get()) }
        single<ActiveHoldStore> { PreferencesActiveHoldStore(get()) }
        single<RecentSearchesStore> { PreferencesRecentSearchesStore(get()) }
        single<WaitlistJoinsStore> { PreferencesWaitlistJoinsStore(get()) }
        // The sign-in (M3-M0): secure storage per platform; and the per-install `X-Device-Id`.
        single<SessionStore> { secureSessionStore(get()) }
        // One store behind both: the id that is renewed is the id that is sent (M3-M5).
        single { PreferencesDeviceIdStore(get()) } binds arrayOf(DeviceIdSource::class, DeviceIdRenewal::class)
        // The install secret `registerDevice` issues (M3-M5), in secure storage like the session.
        single<InstallSecretStore> { secureInstallSecretStore(get()) }
        // The push pre-prompt's "Not now" count (M3-M5).
        single<PushPromptStore> { PreferencesPushPromptStore(get()) }
    }
