package eg.bahr.core.catalog.di

import eg.bahr.core.catalog.data.DefaultHostListRepository
import eg.bahr.core.catalog.data.DefaultTripListRepository
import eg.bahr.core.catalog.data.HostListApiService
import eg.bahr.core.catalog.data.HostListRepository
import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripHearts
import eg.bahr.core.catalog.data.TripLikeApiService
import eg.bahr.core.catalog.data.TripLikeStore
import eg.bahr.core.catalog.data.TripListApiService
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * What feature:home and the trip-area features (M3-M16) share at run time, bound once: one card cache (a card loaded on
 * Home makes the trip page text-first), one `GET /trips` read, one `GET /hosts` read (MV-M1), one waiting-list memory and
 * one store of hearts (MP-M1). Started by the app next to `networkModule` and `dataStoreModule`, whose client, session and
 * store these take, and the app module's `AppErrorController`.
 */
val catalogModule =
    module {
        // The hearts (MP-M1): the app's own scope on the main thread (see TripLikeStore), so a like outlives its screen;
        // ended with the graph (a test's; the app's lives as long as the process).
        single {
            val session = get<AuthSession>()
            val api = TripLikeApiService(get())
            TripLikeStore(
                remote = api::setLiked,
                errors = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                signedIn =
                    session.state
                        // Read once here too: until the secure store has been read, nobody is known to be signed in.
                        .onStart { session.load() }
                        .filterNot { it == SessionState.Unknown }
                        .map { it is SessionState.SignedIn },
            )
        } withOptions {
            bind<TripHearts>()
            onClose { it?.close() }
        }
        single { TripCardCache(get()) }
        single { TripListApiService(get()) }
        single<TripListRepository> { DefaultTripListRepository(get(), get()) }
        // `GET /hosts` (MV-M1): the hosts directory and the hosts above search's results.
        single { HostListApiService(get()) }
        single<HostListRepository> { DefaultHostListRepository(get()) }
        // Over core:datastore's WaitlistJoinsStore (M4-M5).
        single { WaitlistMemory(get()) }
    }
