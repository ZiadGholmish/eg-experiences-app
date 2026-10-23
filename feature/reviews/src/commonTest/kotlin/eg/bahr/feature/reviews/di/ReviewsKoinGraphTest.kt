package eg.bahr.feature.reviews.di

import eg.bahr.core.catalog.di.catalogModule
import eg.bahr.core.datastore.WaitlistJoinsStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.testing.EMPTY_TRIP_PAGE_JSON
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.jsonMockEngine
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.feature.reviews.data.ReviewsRepository
import eg.bahr.feature.reviews.presentation.ReviewsViewModel
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.Test

/**
 * The Koin graph "See all reviews" runs on: `catalogModule` + `reviewsModule`,
 * with only what the app module and core:network / core:datastore provide stubbed (the HTTP client on a MockEngine,
 * the stores). A missing or duplicated binding fails here instead of at app start or on navigation (M4-M1c review #1;
 * split from feature:trips' graph test, M3-M16).
 */
class ReviewsKoinGraphTest {
    /** The graph on a fresh Koin application, closed after (core:testing's [runKoinGraphTest]). */
    private fun withGraph(test: suspend (Koin) -> Unit) =
        runKoinGraphTest(
            listOf(
                catalogModule,
                reviewsModule,
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
    fun `every reviews binding resolves - for a trip and for a host`() =
        withGraph { koin ->
            koin.get<ReviewsRepository>()
            koin.get<ReviewsViewModel> { parametersOf("burullus-dawn", null, "Dawn") }
            koin.get<ReviewsViewModel> { parametersOf(null, "ashraf-el-bahr", null) }
        }
}
