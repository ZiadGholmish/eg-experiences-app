package eg.bahr.core.network

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CallApiTest {
    @Serializable
    data class Payload(
        val name: String,
    )

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        retryAfterHeader: String? = null,
    ) = HttpClient(
        MockEngine { _ ->
            val headers =
                listOfNotNull(
                    "Content-Type" to listOf(ContentType.Application.Json.toString()),
                    retryAfterHeader?.let { "Retry-After" to listOf(it) },
                )
            respond(content = body, status = status, headers = headersOf(*headers.toTypedArray()))
        },
    ) {
        expectSuccess = false
        install(ContentNegotiation) { json(HttpClientFactory.json) }
    }

    @Test
    fun `unwraps the data out of a successful envelope`() =
        runTest {
            val client = clientReturning("""{"success":true,"data":{"name":"Burullus"}}""")

            val result = callApi<Payload> { client.get("trips") }

            assertIs<AppResult.Success<Payload>>(result)
            assertEquals("Burullus", result.data.name)
        }

    @Test
    fun `a success-false envelope is a failure even on HTTP 200`() =
        runTest {
            // The backend answers 200 with success:false in some paths; branching on
            // the status alone would treat that as a win.
            val client =
                clientReturning("""{"success":false,"error":{"code":"NO_SEATS_AVAILABLE","message":"gone"}}""")

            val result = callApi<Payload> { client.get("bookings") }

            assertIs<AppResult.Failure>(result)
            val error = assertIs<AppError.Api>(result.error)
            assertEquals("NO_SEATS_AVAILABLE", error.code)
            assertEquals(200, error.httpStatus)
        }

    @Test
    fun `a body that does not match the contract is a serialization failure`() =
        runTest {
            val client = clientReturning("""{"success":true,"data":{"unexpected":1}}""")

            val result = callApi<Payload> { client.get("trips") }

            assertIs<AppResult.Failure>(result)
            assertIs<AppError.Serialization>(result.error)
        }

    @Test
    fun `an unknown field is ignored so the web client can move first`() =
        runTest {
            // One contract serves two clients; a field added for the web must not
            // break an app build that predates it.
            val client = clientReturning("""{"success":true,"data":{"name":"Burullus","newField":42}}""")

            val result = callApi<Payload> { client.get("trips") }

            assertIs<AppResult.Success<Payload>>(result)
            assertEquals("Burullus", result.data.name)
        }

    @Test
    fun `an empty-body success is accepted for Unit callers`() =
        runTest {
            // A success envelope with no data: ApiResponse.ok(null).
            val client = clientReturning("""{"success":true}""")

            val result = callApi<Unit> { client.get("bookings/BRL-4417/cancel") }

            assertIs<AppResult.Success<Unit>>(result)
        }

    @Test
    fun `a 204 with no body and no content type is a success for Unit callers`() =
        runTest {
            // As the real server answers DELETE /bookings/{ref}/hold: no body, no Content-Type.
            val client =
                HttpClient(MockEngine { _ -> respond(content = "", status = HttpStatusCode.NoContent) }) {
                    expectSuccess = false
                    install(ContentNegotiation) { json(HttpClientFactory.json) }
                }

            val result = callApi<Unit> { client.get("bookings/BRL-4417/hold") }

            assertIs<AppResult.Success<Unit>>(result)
        }

    @Test
    fun `a 204 is not a success for a caller that expects data`() =
        runTest {
            val client =
                HttpClient(MockEngine { _ -> respond(content = "", status = HttpStatusCode.NoContent) }) {
                    expectSuccess = false
                    install(ContentNegotiation) { json(HttpClientFactory.json) }
                }

            val result = callApi<Payload> { client.get("trips") }

            assertIs<AppResult.Failure>(result)
        }

    @Test
    fun `a server error carries its status through`() =
        runTest {
            val client =
                HttpClient(
                    MockEngine { _ -> respondError(HttpStatusCode.InternalServerError) },
                ) {
                    expectSuccess = false
                    install(ContentNegotiation) { json(HttpClientFactory.json) }
                }

            val result = callApi<Payload> { client.get("trips") }

            assertIs<AppResult.Failure>(result)
        }

    /** openapi `HoldBlocked`: 403, `error.retryAfter` and the same figure as `Retry-After` (MB-M1). */
    @Test
    fun `a hold block carries the seconds until holds are taken again`() =
        runTest {
            val client = clientReturning(HOLD_BLOCKED_BODY, HttpStatusCode.Forbidden, retryAfterHeader = "18000")

            val error = assertIs<AppError.Api>((callApi<Payload> { client.post("bookings") } as AppResult.Failure).error)

            assertEquals("HOLD_BLOCKED", error.code)
            assertEquals(403, error.httpStatus)
            assertEquals(18_000L, error.retryAfterSeconds)
        }

    @Test
    fun `the Retry-After header stands in when the body has no retryAfter`() =
        runTest {
            val body = """{"success":false,"data":null,"error":{"code":"HOLD_BLOCKED","message":"Booking is paused"}}"""
            val client = clientReturning(body, HttpStatusCode.Forbidden, retryAfterHeader = "3600")

            val error = assertIs<AppError.Api>((callApi<Payload> { client.post("bookings") } as AppResult.Failure).error)

            assertEquals(3_600L, error.retryAfterSeconds)
        }

    @Test
    fun `no retryAfter anywhere is null - not zero`() =
        runTest {
            val body = """{"success":false,"data":null,"error":{"code":"HOLD_BLOCKED","message":"Booking is paused"}}"""
            val client = clientReturning(body, HttpStatusCode.Forbidden)

            val error = assertIs<AppError.Api>((callApi<Payload> { client.post("bookings") } as AppResult.Failure).error)

            assertNull(error.retryAfterSeconds)
        }

    /** The contract says at least 1: a body figure of 0 or less is dropped like a bad header, never "in 1 minute". */
    @Test
    fun `a body retryAfter of zero or less is ignored`() =
        runTest {
            val zero = """{"success":false,"data":null,"error":{"code":"HOLD_BLOCKED","message":"x","retryAfter":0}}"""
            val withHeader = clientReturning(zero, HttpStatusCode.Forbidden, retryAfterHeader = "3600")
            val negative = """{"success":false,"data":null,"error":{"code":"HOLD_BLOCKED","message":"x","retryAfter":-5}}"""
            val alone = clientReturning(negative, HttpStatusCode.Forbidden)

            val fromHeader =
                assertIs<AppError.Api>((callApi<Payload> { withHeader.post("bookings") } as AppResult.Failure).error)
            val dropped = assertIs<AppError.Api>((callApi<Payload> { alone.post("bookings") } as AppResult.Failure).error)

            assertEquals(3_600L, fromHeader.retryAfterSeconds)
            assertNull(dropped.retryAfterSeconds)
        }

    private companion object {
        /** Shaped on openapi `ErrorEnvelope` + `ApiError` (the contract has no HOLD_BLOCKED example). */
        const val HOLD_BLOCKED_BODY =
            """{"success":false,"data":null,"error":{"code":"HOLD_BLOCKED",""" +
                """"message":"Too many unpaid holds. You can hold seats again in 5 hours.","retryAfter":18000}}"""
    }
}
