package eg.bahr.core.network.auth

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The sign-in rules of M3-M0 through the real client and plugin, on a MockEngine: the bearer token,
 * refreshes serialised (a refresh token is single-use, M3-B1 review #4), sign-out on a refused or lost
 * refresh, tokens kept when the refresh never left, and `X-Device-Id` only where asked for.
 */
@OptIn(ExperimentalAtomicApi::class)
class AuthSessionTest {
    private class MemoryStore(
        var session: StoredSession? = null,
    ) : SessionStore {
        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    private class FixedClock(
        var now: Instant,
    ) : Clock {
        override fun now() = now
    }

    private val clock = FixedClock(Instant.parse("2026-10-13T10:00:00Z"))

    private fun tokens(
        access: String,
        refresh: String,
        accessExpires: String = "2026-10-13T10:15:00Z",
    ) = AuthTokens(access, accessExpires, refresh, "2026-11-13T10:00:00Z")

    private fun signedIn(tokens: AuthTokens) = MemoryStore(StoredSession(tokens, accountName = "Sara", profileComplete = true))

    private val refreshed =
        """{"success":true,"data":{"accessToken":"a2","accessTokenExpiresAt":"2026-10-13T10:30:00Z","refreshToken":"r2",""" +
            """"refreshTokenExpiresAt":"2026-11-13T10:00:00Z","roles":["CUSTOMER"]}}"""
    private val ok = """{"success":true,"data":{"name":"ok"}}"""
    private val unauthorized = """{"success":false,"error":{"code":"UNAUTHORIZED","message":"no"}}"""

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(
        session: AuthSession,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = apiHttpClient(
        engine = MockEngine { request -> handler(request) },
        config = ApiConfig(baseUrl = "http://test/api/v1/", isDebug = false),
        languageTagProvider = { "ar" },
        session = session,
        deviceIds = DeviceIdSource { DEVICE },
    )

    private val HttpRequestData.path get() = url.encodedPath.removePrefix("/api/v1/")
    private val HttpRequestData.bearer get() = headers[HttpHeaders.Authorization]

    private fun HttpRequestData.bodyText() = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    @Test
    fun `a signed-in request carries the access token - a signed-out one none`() =
        runTest {
            val sent = mutableListOf<String?>()
            val session = AuthSession(signedIn(tokens("a1", "r1")), clock)
            val client =
                client(session) { request ->
                    sent += request.bearer
                    json(ok)
                }

            client.get("me")
            session.signOut()
            client.get("trips")

            // me with the token; the sign-out call without one (it carries the refresh token in its body); trips without.
            assertEquals(listOf<String?>("Bearer a1", null, null), sent)
        }

    @Test
    fun `an expiring token is refreshed before the request - once for many requests at once`() =
        runTest {
            clock.now = Instant.parse("2026-10-13T10:14:50Z")
            val store = signedIn(tokens("a1", "r1"))
            val session = AuthSession(store, clock)
            val refreshes = mutableListOf<String>()
            val bearers = mutableListOf<String?>()
            val client =
                client(session) { request ->
                    if (request.path == "auth/refresh") {
                        refreshes += request.bodyText()
                        json(refreshed)
                    } else {
                        bearers += request.bearer
                        json(ok)
                    }
                }

            (1..3).map { async { client.get("me") } }.awaitAll()

            assertEquals(1, refreshes.size, "a refresh token is single-use: one refresh for all")
            assertTrue("\"refreshToken\":\"r1\"" in refreshes.single())
            assertEquals(List<String?>(3) { "Bearer a2" }, bearers)
            assertEquals("r2", store.session?.tokens?.refreshToken)
            assertEquals("Sara", store.session?.accountName, "the account summary is kept")
        }

    @Test
    fun `a 401 is retried once with a refreshed token`() =
        runTest {
            val session = AuthSession(signedIn(tokens("a1", "r1")), clock)
            val bearers = mutableListOf<String?>()
            val client =
                client(session) { request ->
                    when {
                        request.path == "auth/refresh" -> json(refreshed)
                        request.bearer == "Bearer a1" -> json(unauthorized, HttpStatusCode.Unauthorized).also { bearers += request.bearer }
                        else -> json(ok).also { bearers += request.bearer }
                    }
                }

            val response = client.get("me")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf<String?>("Bearer a1", "Bearer a2"), bearers)
        }

    @Test
    fun `a refused refresh signs out and the 401 reaches the caller`() =
        runTest {
            val store = signedIn(tokens("a1", "r1"))
            val session = AuthSession(store, clock)
            val client =
                client(session) { request ->
                    json(unauthorized, HttpStatusCode.Unauthorized).also { assertTrue(request.path in setOf("me", "auth/refresh")) }
                }

            val response = client.get("me")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertNull(store.session)
            assertEquals(SessionState.SignedOut, session.state.value)
        }

    @Test
    fun `a lost refresh answer signs out - the token may already be used`() =
        runTest {
            clock.now = Instant.parse("2026-10-13T10:20:00Z")
            val store = signedIn(tokens("a1", "r1"))
            val session = AuthSession(store, clock)
            val client =
                client(session) { request ->
                    if (request.path == "auth/refresh") throw HttpRequestTimeoutException(request.url.toString(), 1)
                    json(ok)
                }

            client.get("trips")

            assertNull(store.session)
        }

    @Test
    fun `a refresh that never left keeps the tokens for the next try`() =
        runTest {
            clock.now = Instant.parse("2026-10-13T10:20:00Z")
            val store = signedIn(tokens("a1", "r1"))
            val session = AuthSession(store, clock)
            val client =
                client(session) { request ->
                    if (request.path == "auth/refresh") throw IOException("offline")
                    json(ok)
                }

            client.get("trips")

            assertEquals("r1", store.session?.tokens?.refreshToken)
            assertIs<SessionState.SignedIn>(session.state.value)
        }

    @Test
    fun `a request waiting behind a refresh takes the new token instead of refreshing again`() =
        runTest {
            clock.now = Instant.parse("2026-10-13T10:20:00Z")
            val session = AuthSession(signedIn(tokens("a1", "r1")), clock)
            val gate = CompletableDeferred<Unit>()
            val refreshes = AtomicInt(0)
            val bearers = Recorded()
            val client =
                client(session) { request ->
                    if (request.path == "auth/refresh") {
                        refreshes.incrementAndFetch()
                        gate.await()
                        json(refreshed)
                    } else {
                        bearers.add(request.bearer)
                        json(ok)
                    }
                }

            val first = async { client.get("me") }
            // The first request is inside its refresh, holding the session's lock …
            until { refreshes.load() == 1 }
            // … when the second starts and has to wait for it.
            val second = async { client.get("me/bookings") }
            realDelay()
            gate.complete(Unit)
            listOf(first, second).awaitAll()

            assertEquals(1, refreshes.load(), "one refresh: the waiter saw the new token")
            assertEquals(listOf<String?>("Bearer a2", "Bearer a2"), bearers.all())
        }

    @Test
    fun `two requests refused at once with the same token refresh once - and both retry with the new one`() =
        runTest {
            val session = AuthSession(signedIn(tokens("a1", "r1")), clock)
            val gate = CompletableDeferred<Unit>()
            val refreshBodies = Recorded()
            val refused = AtomicInt(0)
            val retried = Recorded()
            val client =
                client(session) { request ->
                    when {
                        request.path == "auth/refresh" -> {
                            refreshBodies.add(request.bodyText())
                            gate.await()
                            json(refreshed)
                        }
                        request.bearer == "Bearer a1" -> {
                            refused.incrementAndFetch()
                            json(unauthorized, HttpStatusCode.Unauthorized)
                        }
                        else -> {
                            retried.add("${request.path} ${request.bearer}")
                            json(ok)
                        }
                    }
                }

            val first = async { client.get("me") }
            val second = async { client.get("me/bookings") }
            // Both refused with a1, and one of them refreshing (holding the lock) while the other waits.
            until { refused.load() == 2 && refreshBodies.all().size == 1 }
            realDelay()
            gate.complete(Unit)
            val statuses = listOf(first, second).awaitAll().map { it.status }

            assertEquals(1, refreshBodies.all().size, "r1 is single-use: sent once, never replayed")
            assertTrue("\"refreshToken\":\"r1\"" in refreshBodies.all().single()!!)
            assertEquals(setOf("me Bearer a2", "me/bookings Bearer a2"), retried.all().toSet())
            assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK), statuses)
        }

    @Test
    fun `refreshed and refused again - the tokens are dropped`() =
        runTest {
            val store = signedIn(tokens("a1", "r1"))
            val session = AuthSession(store, clock)
            val client =
                client(session) { request ->
                    if (request.path == "auth/refresh") json(refreshed) else json(unauthorized, HttpStatusCode.Unauthorized)
                }

            val response = client.get("me")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertNull(store.session)
            assertEquals(SessionState.SignedOut, session.state.value)
        }

    @Test
    fun `a 401 whose refresh cannot be sent still drops the tokens - so the signed-out message is true`() =
        runTest {
            val store = signedIn(tokens("a1", "r1"))
            val session = AuthSession(store, clock)
            val client =
                client(session) { request ->
                    if (request.path == "auth/refresh") throw IOException("no route")
                    json(unauthorized, HttpStatusCode.Unauthorized)
                }

            client.get("me")

            assertNull(store.session)
        }

    @Test
    fun `a rate-limited refresh keeps the tokens - the token was not used`() =
        runTest {
            clock.now = Instant.parse("2026-10-13T10:20:00Z")
            val store = signedIn(tokens("a1", "r1"))
            val session = AuthSession(store, clock)
            val limited = """{"success":false,"error":{"code":"RATE_LIMITED","message":"slow down"}}"""
            val client =
                client(session) { request ->
                    if (request.path == "auth/refresh") json(limited, HttpStatusCode.TooManyRequests) else json(ok)
                }

            client.get("trips")

            assertEquals("r1", store.session?.tokens?.refreshToken)
            assertIs<SessionState.SignedIn>(session.state.value)
        }

    @Test
    fun `a late 401 to an old token does not sign out a session refreshed since`() =
        runTest {
            val store = signedIn(tokens("a2", "r2"))
            val session = AuthSession(store, clock)

            session.dropIfCurrent("a1")

            assertEquals("a2", store.session?.tokens?.accessToken)
        }

    @Test
    fun `the device id goes only on requests that ask for it - and sign-in style requests carry no token`() =
        runTest {
            val session = AuthSession(signedIn(tokens("a1", "r1")), clock)
            val seen = mutableMapOf<String, Pair<String?, String?>>()
            val client =
                client(session) { request ->
                    seen[request.path] = request.headers[HEADER_DEVICE_ID] to request.bearer
                    json(ok)
                }

            client.post("bookings") { withDeviceId() }
            client.post("auth/google") {
                withDeviceId()
                withoutAuth()
            }
            client.get("trips")

            assertEquals(DEVICE to "Bearer a1", seen["bookings"])
            assertEquals(DEVICE to null, seen["auth/google"])
            assertNull(seen["trips"]?.first)
        }

    @Test
    fun `sign-out revokes the refresh token and forgets it even when the server cannot be reached`() =
        runTest {
            val store = signedIn(tokens("a1", "r1"))
            val session = AuthSession(store, clock)
            val revoked = mutableListOf<String>()
            val client =
                client(session) { request ->
                    revoked += request.bodyText()
                    throw IOException("offline")
                }
            client.hashCode()

            session.signOut()

            assertNull(store.session)
            assertTrue(revoked.single().contains("r1"))
            assertFalse(session.state.value is SessionState.SignedIn)
        }

    /** A list the MockEngine's threads append to. */
    private class Recorded {
        private val lock = Mutex()
        private var items = listOf<String?>()

        suspend fun add(item: String?) = lock.withLock { items = items + item }

        fun all(): List<String?> = items
    }

    /** Waits in real time (the engine answers on its own threads) for [condition], failing after a while. */
    private suspend fun until(condition: () -> Boolean) =
        withContext(Dispatchers.Default) {
            withTimeout(WAIT_MILLIS) { while (!condition()) delay(POLL_MILLIS) }
        }

    /** Real time for a request that has to queue on the session's lock to get there. */
    private suspend fun realDelay() = withContext(Dispatchers.Default) { delay(SETTLE_MILLIS) }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 5L
        const val SETTLE_MILLIS = 200L
        const val DEVICE = "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e"
    }
}
