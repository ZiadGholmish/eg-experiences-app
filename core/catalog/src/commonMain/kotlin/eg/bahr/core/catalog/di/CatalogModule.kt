package eg.bahr.core.catalog.di

import eg.bahr.core.catalog.data.DefaultHostListRepository
import eg.bahr.core.catalog.data.DefaultTripListRepository
import eg.bahr.core.catalog.data.HostListApiService
import eg.bahr.core.catalog.data.HostListRepository
import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripListApiService
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.data.WaitlistMemory
import org.koin.dsl.module

/**
 * What feature:home and the trip-area features (M3-M16) share at run time, bound once: one card cache (a card loaded on
 * Home makes the trip page text-first), one `GET /trips` read, one `GET /hosts` read (MV-M1) and one waiting-list memory. Started by
 * the app next to `networkModule` and `dataStoreModule`, whose client and store these take.
 */
val catalogModule =
    module {
        single { TripCardCache() }
        single { TripListApiService(get()) }
        single<TripListRepository> { DefaultTripListRepository(get(), get()) }
        // `GET /hosts` (MV-M1): the hosts directory and the hosts above search's results.
        single { HostListApiService(get()) }
        single<HostListRepository> { DefaultHostListRepository(get()) }
        // Over core:datastore's WaitlistJoinsStore (M4-M5).
        single { WaitlistMemory(get()) }
    }
