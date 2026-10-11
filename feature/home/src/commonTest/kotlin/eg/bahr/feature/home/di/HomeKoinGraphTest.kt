package eg.bahr.feature.home.di

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.di.catalogModule
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.WaitlistJoinsStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.testing.EMPTY_TRIP_PAGE_JSON
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.jsonMockEngine
import eg.bahr.core.testing.runKoinGraphTest
import eg.bahr.feature.home.data.HomePayloads
import eg.bahr.feature.home.data.HomeRepository
import eg.bahr.feature.home.presentation.TripListViewModel
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The Koin graph Home runs on (M4-M1c review #1): `catalogModule` + `homeModule`, with only what the
 * app module and core:network / core:datastore provide stubbed (the HTTP client on a MockEngine, the
 * waiting-list store). A missing or duplicated binding fails here instead of at app start.
 */
class HomeKoinGraphTest {
    /** The graph on a fresh Koin application, closed after (core:testing's [runKoinGraphTest]). */
    private fun withGraph(test: suspend (Koin) -> Unit) =
        runKoinGraphTest(
            listOf(
                catalogModule,
                homeModule,
                module {
                    single<HttpClient> {
                        val engine =
                            jsonMockEngine { request ->
                                if (request.url.encodedPath.endsWith("/home")) HomePayloads.ENGLISH else EMPTY_TRIP_PAGE_JSON
                            }
                        apiHttpClient(engine, ApiConfig(TEST_BASE_URL, isDebug = false)) { "en" }
                    }
                    single<WaitlistJoinsStore> { FakeWaitlistJoinsStore() }
                },
            ),
            test,
        )

    @Test
    fun `every Home binding resolves - and the shared catalog singles are single`() =
        withGraph { koin ->
            koin.get<HomeRepository>()
            koin.get<TripListRepository>()
            koin.get<TripListViewModel>()
            assertSame(koin.get<TripCardCache>(), koin.get<TripCardCache>())
            assertSame(koin.get<WaitlistMemory>(), koin.get<WaitlistMemory>())
        }

    @Test
    fun `a Home row card lands in the app's one card cache - which the trip page reads`() =
        withGraph { koin ->
            val cards = koin.get<TripCardCache>()
            assertNull(cards.card(ROW_CARD), "nothing cached before Home is read")

            assertIs<AppResult.Success<*>>(koin.get<HomeRepository>().home())

            assertEquals("The White Desert, overnight", cards.card(ROW_CARD)?.title)
        }

    private companion object {
        const val ROW_CARD = "white-desert-overnight"
    }
}
