package eg.bahr.core.network.di

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.HostSessionStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.LanguageTagProvider
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.HostHttpClient
import eg.bahr.core.network.auth.HostSession
import eg.bahr.core.network.auth.SessionState
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * M3-M0 review #1: the session reaches the server whether or not anything has built the HTTP client
 * yet. A relaunch straight onto Settings and a tap on "Sign out" is the first network use of the run.
 */
class NetworkModuleTest {
    private class MemoryStore(
        var session: StoredSession?,
    ) : SessionStore {
        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    @Test
    fun `sign-out before any request still revokes the refresh token`() =
        runTest {
            val paths = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    paths += request.url.encodedPath
                    respond("", HttpStatusCode.NoContent)
                }
            val store = MemoryStore(StoredSession(AuthTokens("a1", "2099-01-01T00:00:00Z", "r1", "2099-02-01T00:00:00Z")))
            val app =
                koinApplication {
                    modules(
                        networkModule,
                        module {
                            single { ApiConfig(baseUrl = "http://test/api/v1/", isDebug = false) }
                            single { LanguageTagProvider { "ar" } }
                            single<SessionStore> { store }
                            single { DeviceIdSource { "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e" } }
                            single<HttpClientEngine> { engine }
                        },
                    )
                }
            try {
                // Only the session is resolved, as Settings resolves it: nothing has asked for the client.
                app.koin.get<AuthSession>().signOut()

                assertEquals(listOf("/api/v1/auth/sign-out"), paths)
                assertNull(store.session)
            } finally {
                app.close()
            }
        }

    private class HostMemoryStore(
        var session: StoredSession?,
    ) : HostSessionStore {
        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    private fun stored(
        access: String,
        refresh: String,
    ) = StoredSession(AuthTokens(access, "2099-01-01T00:00:00Z", refresh, "2099-02-01T00:00:00Z"))

    /** What each request carried: its path, its bearer, and (for refresh and sign-out) its body. */
    private data class Seen(
        val path: String,
        val bearer: String?,
        val body: String,
    )

    /**
     * M5-M1: host mode's sign-in lives beside the traveller's. Each client sends its own bearer; a host 401 that no
     * refresh cures, and a host sign-out, end the host's sign-in only; the traveller's tokens are never sent to a host
     * route nor touched.
     */
    @Test
    fun `the host session and client are separate from the travellers`() =
        runTest {
            val seen = mutableListOf<Seen>()
            val engine =
                MockEngine { request ->
                    val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                    seen += Seen(request.url.encodedPath, request.headers[HttpHeaders.Authorization], body)
                    when (request.url.encodedPath) {
                        // The host's access token was revoked (a reset by staff) and its refresh is refused.
                        "/api/v1/host/me" -> respond(UNAUTHORIZED, HttpStatusCode.Unauthorized, JSON)
                        "/api/v1/auth/refresh" -> respond(UNAUTHORIZED, HttpStatusCode.Unauthorized, JSON)
                        "/api/v1/me" -> respond("""{"success":true,"data":{"id":"x"}}""", HttpStatusCode.OK, JSON)
                        else -> respond("", HttpStatusCode.NoContent)
                    }
                }
            val traveller = MemoryStore(stored("customer-access", "customer-refresh"))
            val host = HostMemoryStore(stored("host-access", "host-refresh"))
            val app =
                koinApplication {
                    allowOverride(false)
                    modules(
                        networkModule,
                        module {
                            single { ApiConfig(baseUrl = "http://test/api/v1/", isDebug = false) }
                            single { LanguageTagProvider { "ar" } }
                            single<SessionStore> { traveller }
                            single<HostSessionStore> { host }
                            single { DeviceIdSource { "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e" } }
                            single<HttpClientEngine> { engine }
                        },
                    )
                }
            try {
                val customerClient = app.koin.get<HttpClient>()
                val hostClient = app.koin.get<HostHttpClient>().client

                customerClient.get("me")
                hostClient.get("host/me")

                assertEquals(Seen("/api/v1/me", "Bearer customer-access", ""), seen[0])
                assertEquals("Bearer host-access", seen[1].bearer)
                // The host's refresh sent the host's refresh token, never the traveller's.
                val refresh = seen.single { it.path == "/api/v1/auth/refresh" }
                assertEquals(true, "host-refresh" in refresh.body, refresh.body)
                // Refused: the host is signed out, the traveller is not.
                assertNull(host.session)
                assertIs<SessionState.SignedOut>(
                    app.koin
                        .get<HostSession>()
                        .state.value,
                )
                assertNotNull(traveller.session)
                assertEquals("customer-access", traveller.session?.tokens?.accessToken)
                assertEquals(0, seen.count { it.path.startsWith("/api/v1/host/") && it.bearer == "Bearer customer-access" })

                // Signing out of host mode afterwards changes nothing for the traveller either.
                app.koin.get<HostSession>().signOut()
                assertEquals("customer-access", traveller.session?.tokens?.accessToken)
            } finally {
                app.close()
            }
        }

    @Test
    fun `a host sign-out revokes the hosts refresh token and leaves the traveller signed in`() =
        runTest {
            val seen = mutableListOf<Seen>()
            val engine =
                MockEngine { request ->
                    val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                    seen += Seen(request.url.encodedPath, request.headers[HttpHeaders.Authorization], body)
                    respond("", HttpStatusCode.NoContent)
                }
            val traveller = MemoryStore(stored("customer-access", "customer-refresh"))
            val host = HostMemoryStore(stored("host-access", "host-refresh"))
            val app =
                koinApplication {
                    allowOverride(false)
                    modules(
                        networkModule,
                        module {
                            single { ApiConfig(baseUrl = "http://test/api/v1/", isDebug = false) }
                            single { LanguageTagProvider { "ar" } }
                            single<SessionStore> { traveller }
                            single<HostSessionStore> { host }
                            single { DeviceIdSource { "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e" } }
                            single<HttpClientEngine> { engine }
                        },
                    )
                }
            try {
                app.koin.get<HostSession>().signOut()

                val signOut = seen.single()
                assertEquals("/api/v1/auth/sign-out", signOut.path)
                assertEquals(true, "host-refresh" in signOut.body, signOut.body)
                assertNull(host.session)
                assertEquals("customer-refresh", traveller.session?.tokens?.refreshToken)
                assertIs<SessionState.SignedIn>(app.koin.get<AuthSession>().load())
            } finally {
                app.close()
            }
        }

    private companion object {
        const val UNAUTHORIZED = """{"success":false,"data":null,"error":{"code":"UNAUTHORIZED","message":"x"}}"""
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
