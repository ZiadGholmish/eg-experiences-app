package eg.bahr.feature.search.di

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
import eg.bahr.feature.search.data.FakeRecentSearchesStore
import eg.bahr.feature.search.data.LastSearch
import eg.bahr.feature.search.presentation.SearchTripsViewModel
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * The Koin graph search runs on: `catalogModule` + `searchModule`,
 * with only what the app module and core:network / core:datastore provide stubbed (the HTTP client on a MockEngine,
 * the stores). A missing or duplicated binding fails here instead of at app start or on navigation (M4-M1c review #1;
 * split from feature:trips' graph test, M3-M16).
 */
class SearchKoinGraphTest {
    /** The graph on a fresh Koin application, closed after (core:testing's [runKoinGraphTest]). */
    private fun withGraph(test: suspend (Koin) -> Unit) =
        runKoinGraphTest(
            listOf(
                catalogModule,
                searchModule,
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
    fun `every search binding resolves - and the last search is one for the app`() =
        withGraph { koin ->
            koin.get<SearchTripsViewModel>()
            assertSame(koin.get<LastSearch>(), koin.get<LastSearch>())
        }
}
