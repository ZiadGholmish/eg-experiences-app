package com.egyptexperiences.core.network

import com.egyptexperiences.core.common.result.AppError
import com.egyptexperiences.core.common.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CallApiTest {
    @Serializable
    data class Payload(
        val name: String,
    )

    private fun clientReturning(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = HttpClient(
        MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
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
                clientReturning("""{"success":false,"error":{"code":"SEATS_UNAVAILABLE","message":"gone"}}""")

            val result = callApi<Payload> { client.get("bookings") }

            assertIs<AppResult.Failure>(result)
            val error = assertIs<AppError.Api>(result.error)
            assertEquals("SEATS_UNAVAILABLE", error.code)
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
            // POST /bookings/{ref}/cancel returns ApiResponse.ok(null).
            val client = clientReturning("""{"success":true}""")

            val result = callApi<Unit> { client.get("bookings/BRL-4417/cancel") }

            assertIs<AppResult.Success<Unit>>(result)
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
}
