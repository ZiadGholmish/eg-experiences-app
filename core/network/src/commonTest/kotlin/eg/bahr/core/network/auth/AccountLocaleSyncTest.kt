package eg.bahr.core.network.auth

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M3-M21: the signed-in account's language (`updateMe {locale}`) follows the app's, through the real client on a
 * MockEngine with openapi `AccountEnvelope` answers. A switch, a sign-in to an account in the other language and a
 * restored sign-in whose account language differs or is unknown each send one PATCH; the same language and a signed-out
 * app send nothing; a failure is sent again at the next start.
 */
class AccountLocaleSyncTest {
    private class Sessions(
        var session: StoredSession? = null,
    ) : SessionStore {
        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    private val arrivals = Channel<HttpRequestData>(Channel.UNLIMITED)
    private val language = MutableStateFlow(AppLanguage.ARABIC)

    /** Whether the engine refuses (a 503) instead of answering with the account. */
    private var unavailable = false

    private fun HttpRequestData.json() =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    /**
     * When set, the engine holds every answer until it completes: a request still under way. On the test's own scheduler,
     * so a request the sync makes has reached the engine once [settle] returns (review #2: "nothing sent" is then
     * certain, not a matter of waiting long enough).
     */
    private var hold: CompletableDeferred<Unit>? = null

    /** A sync over [store], not started: what one app start builds. */
    private fun TestScope.sync(store: Sessions): Pair<AuthSession, AccountLocaleSync> {
        val session = AuthSession(store)
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        arrivals.send(request)
                        hold?.await()
                        if (unavailable) {
                            respond(UNAVAILABLE, HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
                        } else {
                            val locale = request.json()["locale"]?.jsonPrimitive?.content
                            respond(account(locale), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                    }
                },
            )
        val client =
            apiHttpClient(
                engine = engine,
                config = ApiConfig(baseUrl = "http://test/api/v1/", isDebug = false),
                session = session,
                languageTagProvider = { language.value.tag },
            )
        return session to AccountLocaleSync(clientProvider = { client }, session = session)
    }

    /** Starts a sync over [store] on the test's background scope: what one app start does. */
    private fun TestScope.start(store: Sessions): AuthSession {
        val (session, sync) = sync(store)
        sync.start(backgroundScope, language)
        return session
    }

    /** Lets the sync decide: everything it would send has been sent once this returns. */
    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(AccountLocaleSync.COALESCE * 2)
        runCurrent()
    }

    @Test
    fun `a restored sign-in whose account is in the other language tells it the app's - once`() =
        runTest {
            val store = Sessions(signedIn(accountLocale = "en"))
            val session = start(store)
            settle()

            val patch = arrivals.next()
            assertEquals(HttpMethod.Patch, patch.method)
            assertEquals("/api/v1/me", patch.url.encodedPath)
            assertEquals("Bearer at", patch.headers[HttpHeaders.Authorization])
            assertEquals(setOf("locale"), patch.json().keys, "only the language: absent fields stay as they are")
            assertEquals("ar", patch.json()["locale"]?.jsonPrimitive?.content)

            session.awaitAccountLocale("ar")
            settle()
            assertNothingSent("the answer is kept, so it is not sent again")
            assertEquals("ar", store.session?.accountLocale, "and kept for the next start")
        }

    @Test
    fun `the same language sends nothing`() =
        runTest {
            start(Sessions(signedIn(accountLocale = "ar")))
            settle()
            assertNothingSent()
        }

    /** The stored language is the first value (the app passes the store's flow, not a default standing in for it). */
    @Test
    fun `an English app with an English account sends nothing`() =
        runTest {
            language.value = AppLanguage.ENGLISH
            start(Sessions(signedIn(accountLocale = "en")))
            settle()
            assertNothingSent()
        }

    @Test
    fun `a switch while signed in is sent`() =
        runTest {
            val session = start(Sessions(signedIn(accountLocale = "ar")))
            settle()
            assertNothingSent()

            language.value = AppLanguage.ENGLISH
            settle()

            assertEquals(
                "en",
                arrivals
                    .next()
                    .json()["locale"]
                    ?.jsonPrimitive
                    ?.content,
            )
            session.awaitAccountLocale("en")
        }

    @Test
    fun `signed out a switch sends nothing - a sign-in to an account in the other language sends the app's`() =
        runTest {
            val session = start(Sessions(session = null))
            settle()
            language.value = AppLanguage.ENGLISH
            settle()
            language.value = AppLanguage.ARABIC
            settle()
            assertNothingSent("the install's registration carries the language of a signed-out app")

            session.signedIn(TOKENS, accountName = "Sara", profileComplete = true, providers = listOf("google"), locale = "en")
            settle()

            assertEquals(
                "ar",
                arrivals
                    .next()
                    .json()["locale"]
                    ?.jsonPrimitive
                    ?.content,
            )
            session.awaitAccountLocale("ar")
        }

    @Test
    fun `a sign-in kept by an older build - account language unknown - is told the app's`() =
        runTest {
            val session = start(Sessions(signedIn(accountLocale = null)))
            settle()

            assertEquals(
                "ar",
                arrivals
                    .next()
                    .json()["locale"]
                    ?.jsonPrimitive
                    ?.content,
            )
            session.awaitAccountLocale("ar")
        }

    @Test
    fun `a failure is not retried in a loop - the next start sends it again`() =
        runTest {
            val store = Sessions(signedIn(accountLocale = "en"))
            unavailable = true
            start(store)
            settle()
            arrivals.next()
            settle()
            assertNothingSent("one try per trigger")
            assertEquals("en", store.session?.accountLocale, "nothing kept, so the next start knows it still differs")

            unavailable = false
            val restarted = start(store)
            settle()

            assertEquals(
                "ar",
                arrivals
                    .next()
                    .json()["locale"]
                    ?.jsonPrimitive
                    ?.content,
            )
            restarted.awaitAccountLocale("ar")
            assertEquals("ar", store.session?.accountLocale)
        }

    /**
     * Review #1: account A's PATCH is under way when A signs out and B, whose account is in English, signs in. A's answer
     * ("ar") must not be kept as B's language: B would then never be told the app's, not even at the next start.
     */
    @Test
    fun `an answer for a sign-in that has ended is dropped - the next account keeps its own and is sent the app's`() =
        runTest {
            val store = Sessions(signedIn(accountLocale = "en"))
            val (session, sync) = sync(store)
            val answer = CompletableDeferred<Unit>().also { hold = it }
            val stale = async { sync.sync("ar") }
            // Virtual time only while A's answer is held: a real-time wait would let the client's timeout fire instead.
            runCurrent()
            assertTrue(arrivals.tryReceive().isSuccess, "A's PATCH is under way")

            session.clear()
            session.signedIn(TOKENS_B, accountName = "Omar", profileComplete = true, providers = listOf("google"), locale = "en")
            answer.complete(Unit)
            assertIs<AppResult.Success<Unit>>(stale.await(), "A's answer did arrive; it is dropped, not lost")

            assertEquals("en", store.session?.accountLocale, "B keeps its own language")
            assertEquals("en", session.accountLocale.value)

            hold = null
            sync.start(backgroundScope, language)
            settle()
            val patch = arrivals.next()
            assertEquals("Bearer at-b", patch.headers[HttpHeaders.Authorization])
            assertEquals("ar", patch.json()["locale"]?.jsonPrimitive?.content)
            session.awaitAccountLocale("ar")
        }

    @Test
    fun `an answer for the same sign-in is kept even when its tokens were refreshed meanwhile`() =
        runTest {
            val expiring = AuthTokens("at-old", "2000-01-01T00:00:00Z", "rt-old", "2099-02-01T00:00:00Z")
            val store = Sessions(StoredSession(expiring, accountName = "Sara", profileComplete = true, accountLocale = "en"))
            val session = AuthSession(store)
            session.remote =
                object : SessionRemote {
                    override suspend fun refresh(refreshToken: String) = RefreshOutcome.Refreshed(TOKENS)

                    override suspend fun revoke(refreshToken: String) = Unit
                }
            val signIn = session.signInGeneration()

            // A request under way refreshes the tokens: the same sign-in, which token equality would get wrong.
            assertEquals("at", session.accessTokenForRequest())
            session.updateAccountLocale("ar", forSignIn = signIn)

            assertEquals("ar", store.session?.accountLocale)
        }

    /** The next request the engine saw; the engine answers on its own thread, so this waits in real time. */
    private suspend fun Channel<HttpRequestData>.next(): HttpRequestData =
        withContext(Dispatchers.Default) {
            withTimeout(ARRIVAL_TIMEOUT_MS) { receive() }
        }

    /**
     * Nothing reached the engine. The engine runs on the test's scheduler, so after [settle] a request would already be
     * in [arrivals]; the short real-time wait is a backstop for any hop the client makes off that scheduler.
     */
    private suspend fun TestScope.assertNothingSent(why: String = "") {
        settle()
        assertTrue(arrivals.tryReceive().isFailure, why)
        assertNull(withContext(Dispatchers.Default) { withTimeoutOrNull(QUIET_MS) { arrivals.receive() } }, why)
    }

    private suspend fun AuthSession.awaitAccountLocale(locale: String) {
        withContext(Dispatchers.Default) { withTimeout(ARRIVAL_TIMEOUT_MS) { accountLocale.first { it == locale } } }
    }

    private fun signedIn(accountLocale: String?) =
        StoredSession(TOKENS, accountName = "Sara", profileComplete = true, accountLocale = accountLocale)

    private companion object {
        const val ARRIVAL_TIMEOUT_MS = 5_000L
        const val QUIET_MS = 300L
        val TOKENS = AuthTokens("at", "2099-01-01T00:00:00Z", "rt", "2099-02-01T00:00:00Z")
        val TOKENS_B = AuthTokens("at-b", "2099-01-01T00:00:00Z", "rt-b", "2099-02-01T00:00:00Z")

        /** openapi `AccountEnvelope`, in the language the PATCH asked for. */
        fun account(locale: String?) =
            """{"success":true,"data":{"id":"8a5d2c1e-0b7f-4e3a-9c6d-1f2e3a4b5c6d","name":"Sara","phone":"01012345678",""" +
                """"locale":"${locale ?: "ar"}","providers":["google"],"profileComplete":true},"error":null}"""

        const val UNAVAILABLE = """{"success":false,"data":null,"error":{"code":"INTERNAL_ERROR","message":"m"}}"""
    }
}
