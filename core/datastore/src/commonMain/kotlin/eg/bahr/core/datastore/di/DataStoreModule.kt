package eg.bahr.core.datastore.di

import eg.bahr.core.datastore.AppSettingsStore
import org.koin.dsl.module

/**
 * The `DataStore<Preferences>` itself is bound by the app module, which is the
 * only place that can produce a platform file path.
 */
val dataStoreModule =
    module {
        single { AppSettingsStore(get()) }
    }
