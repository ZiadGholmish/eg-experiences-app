package eg.bahr.core.network.di

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.LanguageTagProvider
import eg.bahr.core.network.auth.AuthSession
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
