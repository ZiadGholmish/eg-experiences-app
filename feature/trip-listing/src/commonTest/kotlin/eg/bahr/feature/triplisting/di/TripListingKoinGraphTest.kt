package eg.bahr.feature.triplisting.di

import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.di.catalogModule
import eg.bahr.core.datastore.WaitlistJoinsStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.testing.EMPTY_TRIP_PAGE_JSON
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.jsonMockEngine
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.feature.triplisting.data.TripListingRepository
import eg.bahr.feature.triplisting.presentation.CategoryTripsViewModel
import eg.bahr.feature.triplisting.presentation.SectionTripsViewModel
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.Test

/**
 * The Koin graph the category page and "See all" run on: `catalogModule` + `tripListingModule`,
 * with only what the app module and core:network / core:datastore provide stubbed (the HTTP client on a MockEngine,
 * the stores). A missing or duplicated binding fails here instead of at app start or on navigation (M4-M1c review #1;
 * split from feature:trips' graph test, M3-M16).
 */
class TripListingKoinGraphTest {
    /** The graph on a fresh Koin application, closed after (core:testing's [runKoinGraphTest]). */
    private fun withGraph(test: suspend (Koin) -> Unit) =
        runKoinGraphTest(
            listOf(
                catalogModule,
                tripListingModule,
                module {
                    single<HttpClient> {
                        apiHttpClient(jsonMockEngine { EMPTY_TRIP_PAGE_JSON }, ApiConfig(TEST_BASE_URL, isDebug = false)) { "en" }
                    }
                    single<WaitlistJoinsStore> { FakeWaitlistJoinsStore() }
                },
            ),
            test,
        )

    @Test
    fun `every trip list binding resolves`() =
        withGraph { koin ->
            koin.get<TripListingRepository>()
            koin.get<TripListRepository>()
            koin.get<CategoryTripsViewModel> { parametersOf("birds", null) }
            koin.get<SectionTripsViewModel> { parametersOf("s-1", "Featured") }
        }
}
