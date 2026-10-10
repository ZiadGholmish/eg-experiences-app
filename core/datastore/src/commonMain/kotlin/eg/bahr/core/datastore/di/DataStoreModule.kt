package eg.bahr.core.datastore.di

import eg.bahr.core.datastore.ActiveHoldStore
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.datastore.PreferencesActiveHoldStore
import eg.bahr.core.datastore.PreferencesRecentSearchesStore
import eg.bahr.core.datastore.PreferencesWaitlistJoinsStore
import eg.bahr.core.datastore.RecentSearchesStore
import eg.bahr.core.datastore.WaitlistJoinsStore
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
    }
