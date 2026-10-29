package eg.bahr.feature.auth.data

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.jsonMockEngine
import eg.bahr.feature.auth.model.AuthProvider
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The account operations on the real client (MockEngine), payloads from openapi's `SignIn` and
 * `Account`: the provider in the path, the raw nonce in the body, `X-Device-Id` and the install's
 * `X-Install-Secret` on sign-in only (M3-M5: M3-B13 attaches the install's holds and entries only with it), no
 * bearer on sign-in, and the session signed in with the tokens and account summary.
 */
class AuthApiServiceTest {
    private class MemoryStore : SessionStore {
        var session: StoredSession? = null

        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    /** The secret kept for [DEVICE] only, as core:datastore keeps it with its id. */
    private class MemorySecrets : InstallSecretStore {
        override suspend fun read(deviceId: String) = SECRET.takeIf { deviceId == DEVICE }

        override suspend fun write(
            deviceId: String,
            secret: String?,
        ) = Unit
    }

    private val requests = mutableListOf<HttpRequestData>()

    private fun HttpRequestData.bodyText() = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private fun setUp(answer: (HttpRequestData) -> String): Pair<DefaultAuthRepository, Pair<AuthSession, MemoryStore>> {
        val store = MemoryStore()
        val session = AuthSession(store)
        val client =
            apiHttpClient(
                engine =
                    jsonMockEngine { request ->
                        requests += request
                        answer(request)
                    },
                config = ApiConfig(baseUrl = TEST_BASE_URL, isDebug = false),
                languageTagProvider = { "ar" },
                session = session,
                deviceIds = DeviceIdSource { DEVICE },
                installSecrets = MemorySecrets(),
            )
        return DefaultAuthRepository(AuthApiService(client), session) to (session to store)
    }

    @Test
    fun `sign-in posts the token and raw nonce to the provider's path and signs the session in`() =
        runTest {
            val (repository, sessionAndStore) = setUp { SIGN_IN }
            val (session, store) = sessionAndStore

            val result = repository.signIn(AuthProvider.Google, "google.jwt", RAW_NONCE, appleName = null)

            assertIs<AppResult.Success<*>>(result)
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(request.url.encodedPath.endsWith("/auth/google"))
            assertEquals(DEVICE, request.headers["X-Device-Id"])
            assertEquals(SECRET, request.headers["X-Install-Secret"], "the install proves itself (openapi SignInInstallSecret)")
            assertNull(request.headers[HttpHeaders.Authorization], "sign-in carries no bearer token")
            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            assertEquals("google.jwt", body["idToken"]?.jsonPrimitive?.content)
            assertEquals(RAW_NONCE, body["nonce"]?.jsonPrimitive?.content)
            assertFalse("name" in body, "an absent name is left out, not sent as null")

            assertEquals(
                SessionState.SignedIn(accountName = null, profileComplete = false, providers = setOf("google")),
                session.state.value,
            )
            assertEquals(AuthTokens("at", "2099-10-13T10:15:00+03:00", "rt", "2099-11-12T10:00:00+03:00"), store.session?.tokens)
        }

    @Test
    fun `the profile update patches me with the bearer token and keeps the summary in step`() =
        runTest {
            val (repository, sessionAndStore) =
                setUp { request ->
                    if (request.url.encodedPath.endsWith("/auth/apple")) SIGN_IN else ACCOUNT
                }
            val (session, _) = sessionAndStore
            repository.signIn(AuthProvider.Apple, "apple.jwt", RAW_NONCE, appleName = "Sara Ali")

            repository.updateProfile("Sara Ali", "+201001234567")

            val patch = requests.last()
            assertEquals(HttpMethod.Patch, patch.method)
            assertEquals("Bearer at", patch.headers[HttpHeaders.Authorization])
            assertNull(patch.headers["X-Device-Id"])
            assertNull(patch.headers["X-Install-Secret"], "the secret goes only where the contract asks for it")
            assertEquals(SessionState.SignedIn("Sara Ali", profileComplete = true, providers = setOf("google")), session.state.value)
        }

    @Test
    fun `deleting the account sends Apple's code and forgets the session`() =
        runTest {
            val (repository, sessionAndStore) =
                setUp { request ->
                    if (request.method ==
                        HttpMethod.Delete
                    ) {
                        """{"success":true}"""
                    } else {
                        SIGN_IN
                    }
                }
            val (session, store) = sessionAndStore
            repository.signIn(AuthProvider.Apple, "apple.jwt", RAW_NONCE, appleName = null)

            repository.deleteAccount("c0de")

            val delete = requests.last()
            assertEquals(HttpMethod.Delete, delete.method)
            assertEquals(
                "c0de",
                Json
                    .parseToJsonElement(delete.bodyText())
                    .jsonObject["appleAuthorizationCode"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(SessionState.SignedOut, session.state.value)
            assertNull(store.session)
        }

    @Test
    fun `the fake provider makes the token the local backend's fake verifier reads`() =
        runTest {
            val fake = FakeProviderSignIn(DeviceIdSource { DEVICE }, listOf(AuthProvider.Google))

            val result = fake.signIn(AuthProvider.Google, "hashed") as ProviderResult.Success

            assertTrue(result.idToken.startsWith("fake."))
            assertFalse(result.idToken.endsWith("="), "base64url without padding")
            @OptIn(ExperimentalEncodingApi::class)
            val claims =
                Json
                    .parseToJsonElement(
                        Base64.UrlSafe
                            .withPadding(Base64.PaddingOption.ABSENT)
                            .decode(result.idToken.removePrefix("fake."))
                            .decodeToString(),
                    ).jsonObject
            assertEquals("local-google-$DEVICE", claims["sub"]?.jsonPrimitive?.content)
            assertEquals("hashed", claims["nonce"]?.jsonPrimitive?.content)
            assertEquals("google", claims["provider"]?.jsonPrimitive?.content)
        }

    @Test
    fun `sha256 hex matches the standard vectors and the nonce is long enough`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
        val nonce = newRawNonce()
        assertTrue(nonce.length >= MIN_NONCE, "openapi: at least 16 characters")
        assertTrue(nonce != newRawNonce(), "fresh per sign-in")
    }

    private companion object {
        const val DEVICE = "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e"
        const val SECRET = "s3cr3t-issued-by-registerDevice"
        const val RAW_NONCE = "0123456789abcdef0123456789abcdef"
        const val MIN_NONCE = 16

        /** openapi `SignIn`; expiries far ahead, so the real clock never makes the session refresh. */
        const val SIGN_IN =
            """{"success":true,"data":{"accessToken":"at","accessTokenExpiresAt":"2099-10-13T10:15:00+03:00","refreshToken":"rt",""" +
                """"refreshTokenExpiresAt":"2099-11-12T10:00:00+03:00","roles":["CUSTOMER"],"newAccount":true,""" +
                """"account":{"id":"8a5d2c1e-0b7f-4e3a-9c6d-1f2e3a4b5c6d","locale":"ar","providers":["google"],"profileComplete":false}}}"""

        /** openapi `Account` after `updateMe`. */
        const val ACCOUNT =
            """{"success":true,"data":{"id":"8a5d2c1e-0b7f-4e3a-9c6d-1f2e3a4b5c6d","name":"Sara Ali","phone":"+201001234567",""" +
                """"locale":"ar","providers":["google"],"profileComplete":true}}"""
    }
}
