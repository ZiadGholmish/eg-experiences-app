package eg.bahr.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.AppSettingsStore
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.TEST_BASE_URL
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Profile's account operations on the real client (MockEngine), payloads from openapi's `Account`
 * (M3-M0b): `getMe` with the bearer token, `updateMe` sending exactly `name` and `phone` (our names,
 * not the handoff's `fullName`/`mobile`), the saved account kept for Profile and the session's summary
 * kept in step, and a 400 `VALIDATION_FAILED` returned as its code.
 */
class ProfileApiServiceTest {
    private class MemoryStore : SessionStore {
        var session: StoredSession? = null

        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    private val requests = mutableListOf<HttpRequestData>()

    private fun HttpRequestData.bodyText() = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private suspend fun setUp(answer: (HttpRequestData) -> Pair<HttpStatusCode, String>): Pair<DefaultSettingsRepository, AuthSession> {
        val session = AuthSession(MemoryStore())
        session.signedIn(TOKENS, accountName = "Menna", profileComplete = false, providers = listOf("google"))
        val client =
            apiHttpClient(
                engine =
                    MockEngine { request ->
                        requests += request
                        val (status, body) = answer(request)
                        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    },
                config = ApiConfig(baseUrl = TEST_BASE_URL, isDebug = false),
                languageTagProvider = { "ar" },
                session = session,
                deviceIds = DeviceIdSource { "device" },
            )
        return DefaultSettingsRepository(AppSettingsStore(InMemoryPreferences()), session, ProfileApiService(client)) to session
    }

    /**
     * Review R2-1: the server wins. The session still holds the name from sign-in ("Menna", incomplete);
     * `/me` says "Menna Hassan", complete. The read is kept, and the session's summary follows it.
     */
    @Test
    fun `getMe reads the account with the bearer token - the server's account wins over the session's summary`() =
        runTest {
            val (repository, session) = setUp { HttpStatusCode.OK to ACCOUNT }

            val result = repository.refreshAccount()

            assertIs<AppResult.Success<*>>(result)
            val get = requests.single()
            assertEquals(HttpMethod.Get, get.method)
            assertEquals("/api/v1/me", get.url.encodedPath)
            assertEquals("Bearer at", get.headers[HttpHeaders.Authorization])
            assertEquals("menna.hassan@gmail.com", repository.account.first()?.email)
            assertEquals(SessionState.SignedIn("Menna Hassan", profileComplete = true, providers = setOf("google")), session.state.value)
        }

    @Test
    fun `updateMe patches name and phone - and the session's summary follows`() =
        runTest {
            val (repository, session) = setUp { HttpStatusCode.OK to ACCOUNT }

            repository.updateProfile("Menna Hassan", "01012345678")

            val patch = requests.single()
            assertEquals(HttpMethod.Patch, patch.method)
            val body = Json.parseToJsonElement(patch.bodyText()).jsonObject
            assertEquals(setOf("name", "phone"), body.keys)
            assertEquals("01012345678", body["phone"]?.jsonPrimitive?.content)
            assertEquals(SessionState.SignedIn("Menna Hassan", profileComplete = true, providers = setOf("google")), session.state.value)
            assertEquals("Menna Hassan", repository.account.first()?.name)

            // Signed out (here: a refused refresh elsewhere), the account goes with the session (review #9).
            session.clear()
            assertNull(repository.account.first())

            // And it does not come back for the next sign-in with the same name (review R2-4).
            session.signedIn(TOKENS, accountName = "Menna Hassan", profileComplete = true, providers = listOf("google"))
            assertNull(repository.account.first())
        }

    @Test
    fun `a refused phone is a VALIDATION_FAILED - nothing is kept`() =
        runTest {
            val (repository, session) = setUp { HttpStatusCode.BadRequest to VALIDATION_FAILED }

            val result = repository.updateProfile("Menna Hassan", "01012345678")

            assertEquals(ApiErrorCodes.VALIDATION_FAILED, ((result as AppResult.Failure).error as AppError.Api).code)
            assertNull(repository.account.first())
            assertEquals("Menna", (session.state.value as SessionState.SignedIn).accountName)
        }

    private companion object {
        val TOKENS = AuthTokens("at", "2099-10-13T10:15:00+03:00", "rt", "2099-11-12T10:00:00+03:00")

        /** openapi `AccountEnvelope`. */
        const val ACCOUNT =
            """{"success":true,"data":{"id":"8a5d2c1e-0b7f-4e3a-9c6d-1f2e3a4b5c6d","name":"Menna Hassan","phone":"01012345678",""" +
                """"email":"menna.hassan@gmail.com","locale":"ar","providers":["google"],"profileComplete":true}}"""

        /** openapi `ErrorEnvelope` for a bad phone. */
        const val VALIDATION_FAILED =
            """{"success":false,"data":null,"error":{"code":"VALIDATION_FAILED","message":"x",""" +
                """"violations":[{"field":"phone","message":"x"}]}}"""
    }
}

/** A DataStore without a file, so the test runs the same on the JVM and on iOS. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
