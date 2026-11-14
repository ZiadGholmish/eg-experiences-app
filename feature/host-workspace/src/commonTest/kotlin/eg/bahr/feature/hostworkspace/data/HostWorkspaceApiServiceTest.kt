package eg.bahr.feature.hostworkspace.data

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.auth.HostSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.network.hostApiHttpClient
import eg.bahr.core.testing.TEST_BASE_URL
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M5-M1: host mode's requests through the real host client (core:network's `hostApiHttpClient`) on a MockEngine, with
 * the contract's example payloads: body-mode sign-in (never `X-Requested-With`), the forced change, the host's bearer,
 * a manual booking's key and language, and answers from a server before M5-B5.
 */
class HostWorkspaceApiServiceTest {
    private class MemoryStore(
        var session: StoredSession? = null,
    ) : SessionStore {
        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    private data class Sent(
        val path: String,
        val headers: Map<String, String?>,
        val body: String,
    )

    private fun HttpRequestData.sent() =
        Sent(
            url.encodedPath,
            listOf(
                HttpHeaders.Authorization,
                "X-Requested-With",
                "Idempotency-Key",
                HttpHeaders.AcceptLanguage,
            ).associateWith { headers[it] },
            (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty(),
        )

    private fun service(
        store: MemoryStore,
        answer: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): Triple<HostWorkspaceRepository, HostSession, MutableList<Sent>> {
        val sent = mutableListOf<Sent>()
        val engine =
            MockEngine { request ->
                sent += request.sent()
                val (status, body) = answer(request)
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val session = HostSession(store)
        val client = hostApiHttpClient(engine, ApiConfig(baseUrl = TEST_BASE_URL, isDebug = false), session) { "ar" }
        return Triple(DefaultHostWorkspaceRepository(HostWorkspaceApiService(client), session), session, sent)
    }

    private fun json(body: String) = Json.parseToJsonElement(body).jsonObject

    @Test
    fun `a sign-in takes the refresh token in the body and keeps it in host modes store`() =
        runTest {
            val store = MemoryStore()
            val (repository, session, sent) = service(store) { HttpStatusCode.OK to SIGNED_IN }
            val result = repository.signIn(" ashraf@example.com ", "one-time")

            assertEquals(AppResult.Success(HostSignInOutcome.SignedIn), result)
            val request = sent.single()
            assertEquals("/api/v1/auth/password", request.path)
            assertNull(request.headers["X-Requested-With"], "cookie mode needs this header; the app never sends it")
            assertNull(request.headers[HttpHeaders.Authorization])
            val body = json(request.body)
            assertEquals("BODY", body["refreshTokenIn"]?.jsonPrimitive?.content)
            assertEquals("ashraf@example.com", body["email"]?.jsonPrimitive?.content)
            assertEquals("host-refresh", store.session?.tokens?.refreshToken)
            assertIs<SessionState.SignedIn>(session.state.value)
        }

    @Test
    fun `a new login gets its change token and nothing is kept until the change`() =
        runTest {
            val store = MemoryStore()
            val (repository, _, sent) =
                service(store) { request ->
                    if (request.url.encodedPath.endsWith("/change")) {
                        HttpStatusCode.OK to TOKENS_ENVELOPE
                    } else {
                        HttpStatusCode.OK to
                            CHANGE_REQUIRED
                    }
                }
            val outcome = repository.signIn("ashraf@example.com", "one-time")
            assertEquals(AppResult.Success(HostSignInOutcome.PasswordChangeRequired("change-token-1")), outcome)
            assertNull(store.session)

            assertEquals(AppResult.Success(Unit), repository.changePassword("change-token-1", "a-long-new-password"))
            val change = sent.last()
            assertEquals("/api/v1/auth/password/change", change.path)
            assertNull(change.headers[HttpHeaders.Authorization], "the change token goes in the body, never as a bearer")
            assertEquals("change-token-1", json(change.body)["changeToken"]?.jsonPrimitive?.content)
            assertEquals("BODY", json(change.body)["refreshTokenIn"]?.jsonPrimitive?.content)
            assertEquals("host-refresh", store.session?.tokens?.refreshToken)
        }

    @Test
    fun `a signed-in session without a refresh token is not kept`() =
        runTest {
            val store = MemoryStore()
            val (repository, _, _) = service(store) { HttpStatusCode.OK to SIGNED_IN.replace(""""refreshToken":"host-refresh",""", "") }
            assertTrue(repository.signIn("a@b.c", "p") is AppResult.Failure)
            assertNull(store.session)
        }

    @Test
    fun `host routes carry the hosts bearer - and a manual booking its key and the guests language`() =
        runTest {
            val store = MemoryStore(signedIn())
            val (repository, _, sent) = service(store) { HttpStatusCode.Created to MANIFEST }
            val result = repository.createManualBooking("dep-1", "key-123", ManualBooking("Karim", "01044127739", 2, false, "en"))

            val manifest = (result as AppResult.Success).data
            assertEquals(2, manifest.bookings.size)
            val request = sent.single()
            assertEquals("/api/v1/host/departures/dep-1/manual-bookings", request.path)
            assertEquals("Bearer host-access", request.headers[HttpHeaders.Authorization])
            assertEquals("key-123", request.headers["Idempotency-Key"])
            assertEquals("ar", request.headers[HttpHeaders.AcceptLanguage])
            assertEquals("en", json(request.body)["locale"]?.jsonPrimitive?.content)
        }

    @Test
    fun `the manifest parses - held seats - a last broadcast and an erased guest included`() =
        runTest {
            val (repository, _, _) = service(MemoryStore(signedIn())) { HttpStatusCode.OK to MANIFEST }
            val manifest = (repository.departure("dep-1") as AppResult.Success).data
            assertEquals(6300, manifest.collected.amount)
            assertEquals("2026-11-20T18:30:00+02:00", manifest.broadcast?.nextAllowedAt)
            assertNull(manifest.bookings.last().phone)
            assertTrue(manifest.bookings.last().manual)
        }

    @Test
    fun `answers from a server before M5-B5 still read - no profile - no traveller id`() =
        runTest {
            val (repository, _, _) =
                service(MemoryStore(signedIn())) { request ->
                    if (request.url.encodedPath.endsWith("/me")) {
                        HttpStatusCode.OK to
                            """{"success":true,"data":{"id":"h1","slug":"ashraf","name":"Ashraf","email":"a@b.c"},"error":null}"""
                    } else {
                        HttpStatusCode.OK to MANIFEST.replace(""""id":"b1",""", "").replace(""""id":"b2",""", "")
                    }
                }
            val me = (repository.me() as AppResult.Success).data
            assertNull(me.login)
            val manifest = (repository.departure("dep-1") as AppResult.Success).data
            assertTrue(manifest.bookings.all { it.id == null })
        }

    @Test
    fun `a host 401 that no refresh cures signs host mode out`() =
        runTest {
            val store = MemoryStore(signedIn())
            val (repository, session, _) = service(store) { HttpStatusCode.Unauthorized to UNAUTHORIZED }
            assertTrue(repository.departures() is AppResult.Failure)
            assertNull(store.session)
            assertIs<SessionState.SignedOut>(session.state.value)
        }

    private fun signedIn() = StoredSession(AuthTokens("host-access", "2099-01-01T00:00:00Z", "host-refresh-0", "2099-02-01T00:00:00Z"))

    private companion object {
        const val TOKENS =
            """{"accessToken":"host-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"host-refresh",""" +
                """"refreshTokenExpiresAt":"2099-02-01T00:00:00Z","roles":["HOST"],"serverNow":"2026-11-20T10:00:00Z"}"""
        const val SIGNED_IN = """{"success":true,"data":{"outcome":"SIGNED_IN","session":$TOKENS},"error":null}"""
        const val CHANGE_REQUIRED =
            """{"success":true,"data":{"outcome":"PASSWORD_CHANGE_REQUIRED","passwordChange":""" +
                """{"changeToken":"change-token-1","expiresAt":"2026-11-20T10:10:00Z"}},"error":null}"""
        val TOKENS_ENVELOPE = """{"success":true,"data":$TOKENS,"error":null}"""
        const val UNAUTHORIZED = """{"success":false,"data":null,"error":{"code":"UNAUTHORIZED","message":"x"}}"""
        const val MANIFEST =
            """{"success":true,"data":{"id":"dep-1","date":"2026-11-21","dayLabel":"Sat 21 Nov","status":"OPEN",""" +
                """"trip":{"slug":"burullus-dawn-boat","title":"Dawn on Lake Burullus","departTime":"05:00","returnTime":null},""" +
                """"seatsBooked":3,"seatsHeld":2,"capacity":18,"collected":{"amount":6300,"amountPiastres":630000,"currency":"EGP"},""" +
                """"bookings":[{"id":"b1","ref":"BAHR-7K2M9QXA","name":"Mona Adel","phone":"+201001234567","partySize":2,"paid":true,""" +
                """"source":"APP","bookedAt":"2026-11-10T18:42:07+02:00"},{"id":"b2","ref":"BAHR-3H8N2RTB","name":"—","phone":null,""" +
                """"partySize":1,"paid":false,"source":"MANUAL","bookedAt":"2026-11-09T10:00:00+02:00"}],""" +
                """"broadcast":{"lastSentAt":"2026-11-20T18:00:00+02:00","recipients":9,"nextAllowedAt":"2026-11-20T18:30:00+02:00"}},"error":null}"""
    }
}
