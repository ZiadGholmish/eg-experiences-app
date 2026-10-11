package eg.bahr.feature.home.di

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.catalog.di.catalogModule
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.WaitlistJoinsStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.home.data.FakeWaitlistJoinsStore
import eg.bahr.feature.home.data.HomePayloads
import eg.bahr.feature.home.data.HomeRepository
import eg.bahr.feature.home.presentation.TripListViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
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
    /**
     * Runs [test] on a fresh graph and closes it after. Closing matters: Koin keeps a `single`'s
     * instance in the module's definition, and the modules are shared `val`s, so an unclosed graph
     * would hand its card cache to the next test's graph.
     */
    private fun withGraph(test: suspend (Koin) -> Unit) =
        runViewModelTest {
            val app = graph()
            try {
                test(app.koin)
            } finally {
                app.close()
            }
        }

    private fun graph(): KoinApplication =
        koinApplication {
            modules(
                catalogModule,
                homeModule,
                module {
                    single<HttpClient> {
                        val engine =
                            MockEngine { request ->
                                val body = if (request.url.encodedPath.endsWith("/home")) HomePayloads.ENGLISH else EMPTY_PAGE
                                respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                            }
                        apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "en" }
                    }
                    single<WaitlistJoinsStore> { FakeWaitlistJoinsStore() }
                },
            )
        }

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
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
        const val ROW_CARD = "white-desert-overnight"
        const val EMPTY_PAGE = """{"success":true,"data":{"items":[],"page":0,"size":20,"totalItems":0,"totalPages":0}}"""
    }
}
