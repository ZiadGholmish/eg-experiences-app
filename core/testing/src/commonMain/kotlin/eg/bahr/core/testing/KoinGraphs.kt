package eg.bahr.core.testing

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.TestResult
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication

/*
 * What the Koin graph tests share (M4-M7, M4-M1c round 2 #1): the graph's lifecycle and a JSON
 * MockEngine. The HTTP client itself (core:network's `apiHttpClient`) and the modules under test
 * (core:catalog's, a feature's) are each test's own: core:testing may see core:common and
 * core:datastore only.
 */

/**
 * Runs [test] on a fresh Koin graph of [modules], under [runViewModelTest] so the view models it
 * resolves have a Main dispatcher, and closes the graph after.
 *
 * Closing matters: Koin keeps a `single`'s instance in the module's definition, and modules are shared
 * `val`s, so an unclosed graph would hand its singles (a card cache, say) to the next test's graph.
 * Overrides are refused, so a type bound twice across [modules] fails here as it would at app start.
 */
fun runKoinGraphTest(
    modules: List<Module>,
    test: suspend (Koin) -> Unit,
): TestResult =
    runViewModelTest {
        val app =
            koinApplication {
                allowOverride(false)
                modules(modules)
            }
        try {
            test(app.koin)
        } finally {
            app.close()
        }
    }

/** A MockEngine that answers every request with [body]'s JSON for it, as the api does (status 200). */
fun jsonMockEngine(body: (HttpRequestData) -> String): MockEngine =
    MockEngine { request ->
        respond(body(request), headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

/** The local flavor's base URL; a MockEngine never reaches it. */
const val TEST_BASE_URL = "http://10.0.2.2:8084/api/v1/"

/** An empty `PageResponse<TripCard>` in the api's envelope: what a trip list answers with no trips. */
const val EMPTY_TRIP_PAGE_JSON = """{"success":true,"data":{"items":[],"page":0,"size":20,"totalItems":0,"totalPages":0}}"""
