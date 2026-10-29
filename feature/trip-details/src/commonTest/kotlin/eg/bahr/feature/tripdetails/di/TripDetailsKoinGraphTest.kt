package eg.bahr.feature.tripdetails.di

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.di.catalogModule
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.datastore.WaitlistJoinsStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.testing.EMPTY_TRIP_PAGE_JSON
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.jsonMockEngine
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.feature.tripdetails.data.TripDetailRepository
import eg.bahr.feature.tripdetails.data.TripFixtures.trip
import eg.bahr.feature.tripdetails.presentation.TripDetailViewModel
import eg.bahr.feature.tripdetails.presentation.TripLink
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The Koin graph the trip page runs on: `catalogModule` + `tripDetailsModule`,
 * with only what the app module and core:network / core:datastore provide stubbed (the HTTP client on a MockEngine,
 * the stores). A missing or duplicated binding fails here instead of at app start or on navigation (M4-M1c review #1;
 * split from feature:trips' graph test, M3-M16).
 */
class TripDetailsKoinGraphTest {
    /** The graph on a fresh Koin application, closed after (core:testing's [runKoinGraphTest]). */
    private fun withGraph(test: suspend (Koin) -> Unit) =
        runKoinGraphTest(
            listOf(
                catalogModule,
                tripDetailsModule,
                module {
                    single<HttpClient> {
                        apiHttpClient(jsonMockEngine { EMPTY_TRIP_PAGE_JSON }, ApiConfig(TEST_BASE_URL, isDebug = false)) { "en" }
                    }
                    single<WaitlistJoinsStore> { FakeWaitlistJoinsStore() }
                    // core:network's sign-in (the waiting-list email prefill, M3-M5), signed out.
                    single {
                        AuthSession(
                            object : SessionStore {
                                override suspend fun read(): StoredSession? = null

                                override suspend fun write(session: StoredSession?) = Unit
                            },
                        )
                    }
                },
            ),
            test,
        )

    @Test
    fun `every trip page binding resolves - and the shared catalog singles are single`() =
        withGraph { koin ->
            koin.get<TripDetailRepository>()
            koin.get<TripDetailViewModel> { parametersOf("burullus-dawn", TripLink()) }
            assertSame(koin.get<TripCardCache>(), koin.get<TripCardCache>())
            assertSame(koin.get<WaitlistMemory>(), koin.get<WaitlistMemory>())
        }

    @Test
    fun `a card Home put in the app's one card cache is the trip page's cached card`() =
        withGraph { koin ->
            val repository = koin.get<TripDetailRepository>()
            val card = trip(1, title = "Dawn on Lake Burullus")
            assertNull(repository.cachedCard(card.slug))

            // What feature:home's repository does with a row's cards, through the same single.
            koin.get<TripCardCache>().remember(listOf(card))

            assertEquals(card, repository.cachedCard(card.slug))
        }
}
