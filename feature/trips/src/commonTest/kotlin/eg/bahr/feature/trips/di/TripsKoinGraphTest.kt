package eg.bahr.feature.trips.di

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.di.catalogModule
import eg.bahr.core.datastore.RecentSearchesStore
import eg.bahr.core.datastore.WaitlistJoinsStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.testing.EMPTY_TRIP_PAGE_JSON
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.jsonMockEngine
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.feature.trips.data.FakeRecentSearchesStore
import eg.bahr.feature.trips.data.TripFixtures.trip
import eg.bahr.feature.trips.data.TripRepository
import eg.bahr.feature.trips.presentation.CategoryTripsViewModel
import eg.bahr.feature.trips.presentation.HostProfileViewModel
import eg.bahr.feature.trips.presentation.SearchTripsViewModel
import eg.bahr.feature.trips.presentation.SectionTripsViewModel
import eg.bahr.feature.trips.presentation.TripDetailViewModel
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The Koin graph the trip lists and the trip page run on (M4-M1c review #1): `catalogModule` +
 * `tripsModule`, with only what the app module and core:network / core:datastore provide stubbed (the
 * HTTP client on a MockEngine, the two stores). A missing or duplicated binding fails here instead of
 * at app start or on navigation.
 */
class TripsKoinGraphTest {
    /** The graph on a fresh Koin application, closed after (core:testing's [runKoinGraphTest]). */
    private fun withGraph(test: suspend (Koin) -> Unit) =
        runKoinGraphTest(
            listOf(
                catalogModule,
                tripsModule,
                module {
                    single<HttpClient> {
                        apiHttpClient(jsonMockEngine { EMPTY_TRIP_PAGE_JSON }, ApiConfig(TEST_BASE_URL, isDebug = false)) { "en" }
                    }
                    single<WaitlistJoinsStore> { FakeWaitlistJoinsStore() }
                    single<RecentSearchesStore> { FakeRecentSearchesStore() }
                },
            ),
            test,
        )

    @Test
    fun `every trips binding resolves - and the shared catalog singles are single`() =
        withGraph { koin ->
            koin.get<TripRepository>()
            koin.get<TripListRepository>()
            koin.get<TripDetailViewModel> { parametersOf("burullus-dawn") }
            koin.get<CategoryTripsViewModel> { parametersOf("birds", null) }
            koin.get<SectionTripsViewModel> { parametersOf("s-1", "Featured") }
            koin.get<SearchTripsViewModel>()
            koin.get<HostProfileViewModel> { parametersOf("red-sea-days", "Red Sea Days", null) }
            assertSame(koin.get<TripCardCache>(), koin.get<TripCardCache>())
            assertSame(koin.get<WaitlistMemory>(), koin.get<WaitlistMemory>())
        }

    @Test
    fun `a card Home put in the app's one card cache is the trip page's cached card`() =
        withGraph { koin ->
            val repository = koin.get<TripRepository>()
            val card = trip(1, title = "Dawn on Lake Burullus")
            assertNull(repository.cachedCard(card.slug))

            // What feature:home's repository does with a row's cards, through the same single.
            koin.get<TripCardCache>().remember(listOf(card))

            assertEquals(card, repository.cachedCard(card.slug))
        }
}
