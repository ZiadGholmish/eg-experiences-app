package eg.bahr.core.network.install

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdRenewal
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.push.PushChannel
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.network.auth.AuthSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `registerDevice` (M3-B12 contract, M3-M5) through the real client on a MockEngine, payloads from openapi's
 * `DeviceRequest` / `DeviceRegistrationEnvelope`: the first registration sends no secret and keeps the one issued; later
 * ones send it; registrations never overlap; `INSTALL_SECRET_INVALID` starts over under a new id once and never signs
 * out; any other failure leaves the id alone.
 */
class InstallRegistrarTest {
    private class Ids :
        DeviceIdSource,
        DeviceIdRenewal {
        var id = FIRST_ID
        var renewals = 0

        override suspend fun deviceId() = id

        override suspend fun renewDeviceId(): String {
            renewals++
            id = SECOND_ID
            return id
        }
    }

    private class Secrets : InstallSecretStore {
        val kept = mutableMapOf<String, String>()

        override suspend fun read(deviceId: String) = kept[deviceId]

        override suspend fun write(
            deviceId: String,
            secret: String?,
        ) {
            kept.clear()
            secret?.let { kept[deviceId] = it }
        }
    }

    private class Push(
        token: String? = null,
    ) : PushChannel {
        override val isAvailable = true
        val tokens = MutableStateFlow(token)
        override val token: StateFlow<String?> = tokens

        override suspend fun notificationsAllowed() = tokens.value != null

        override fun refresh() = Unit
    }

    private class Sessions(
        var session: StoredSession? = null,
    ) : SessionStore {
        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    private val requests = mutableListOf<HttpRequestData>()
    private val ids = Ids()
    private val secrets = Secrets()

    private fun HttpRequestData.json() =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    private fun registrar(
        push: Push = Push(),
        session: AuthSession = AuthSession(Sessions()),
        answer: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): InstallRegistrar {
        val engine =
            MockEngine { request ->
                requests += request
                val (status, body) = answer(request)
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val client =
            apiHttpClient(
                engine = engine,
                config = ApiConfig(baseUrl = "http://test/api/v1/", isDebug = false),
                session = session,
                deviceIds = ids,
                languageTagProvider = { "en" },
            )
        return InstallRegistrar(
            clientProvider = { client },
            deviceIds = ids,
            renewal = ids,
            secrets = secrets,
            push = push,
            session = session,
            language = { "en" },
            platform = "ANDROID",
        )
    }

    @Test
    fun `the first registration sends no secret and keeps the one issued - the next one sends it`() =
        runTest {
            val registrar = registrar { (if (requests.size == 1) ISSUED else NOT_ISSUED).ok() }

            assertIs<AppResult.Success<Unit>>(registrar.register())
            registrar.register()

            val (first, second) = requests
            assertEquals(HttpMethod.Post, first.method)
            assertTrue(first.url.encodedPath.endsWith("/api/v1/devices"))
            assertEquals(FIRST_ID, first.headers["X-Device-Id"])
            assertNull(first.headers["X-Install-Secret"], "none issued yet")
            assertEquals(SECRET, secrets.kept[FIRST_ID])
            assertEquals(SECRET, second.headers["X-Install-Secret"])
            assertEquals(SECRET, secrets.kept[FIRST_ID], "a null installSecret keeps the one kept")
        }

    @Test
    fun `without push permission the install registers with no token - with it - the token and platform go`() =
        runTest {
            val push = Push(token = null)
            val registrar = registrar(push) { NOT_ISSUED.ok() }

            registrar.register()
            push.tokens.value = "fcm-token-1"
            registrar.register()

            val (without, with) = requests.map { it.json() }
            assertFalse("token" in without, "left out, not null (openapi DeviceRequest: token optional)")
            assertEquals("ANDROID", without["platform"]?.jsonPrimitive?.content)
            assertEquals("en", without["locale"]?.jsonPrimitive?.content)
            assertEquals("fcm-token-1", with["token"]?.jsonPrimitive?.content)
        }

    @Test
    fun `a signed-in registration carries the bearer token - so the install is linked to the account`() =
        runTest {
            val tokens = AuthTokens("at", "2099-01-01T00:00:00Z", "rt", "2099-02-01T00:00:00Z")
            val registrar = registrar(session = AuthSession(Sessions(StoredSession(tokens)))) { NOT_ISSUED.ok() }

            registrar.register()

            assertEquals("Bearer at", requests.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun `registrations never overlap - the second waits for the first's answer and sends its secret`() =
        runTest {
            var inFlight = 0
            var mostAtOnce = 0
            val registrar =
                registrar {
                    inFlight++
                    mostAtOnce = maxOf(mostAtOnce, inFlight)
                    // Real time on the engine's own thread: without the lock the second would land meanwhile.
                    delay(OVERLAP_WINDOW_MS)
                    inFlight--
                    (if (requests.size == 1) ISSUED else NOT_ISSUED).ok()
                }

            listOf(async { registrar.register() }, async { registrar.register() }).awaitAll()

            assertEquals(1, mostAtOnce, "one registration of an install at a time")
            assertNull(requests[0].headers["X-Install-Secret"])
            assertEquals(SECRET, requests[1].headers["X-Install-Secret"], "the second sends what the first was issued")
            assertEquals(0, ids.renewals)
        }

    @Test
    fun `a refused secret starts over under a new id once - and keeps the sign-in`() =
        runTest {
            secrets.kept[FIRST_ID] = "stale"
            val tokens = AuthTokens("at", "2099-01-01T00:00:00Z", "rt", "2099-02-01T00:00:00Z")
            val sessions = Sessions(StoredSession(tokens))
            val registrar =
                registrar(session = AuthSession(sessions)) { request ->
                    if (request.headers["X-Device-Id"] == FIRST_ID) HttpStatusCode.Forbidden to SECRET_REFUSED else ISSUED.ok()
                }

            assertIs<AppResult.Success<Unit>>(registrar.register())

            assertEquals(listOf(FIRST_ID, SECOND_ID), requests.map { it.headers["X-Device-Id"] })
            assertNull(requests[1].headers["X-Install-Secret"], "a new install has no secret yet")
            assertEquals(1, ids.renewals)
            assertEquals(mapOf(SECOND_ID to SECRET), secrets.kept.toMap())
            assertEquals(tokens, sessions.session?.tokens, "INSTALL_SECRET_INVALID is not a sign-in problem")
        }

    @Test
    fun `refused again under the new id - it stops there - no loop`() =
        runTest {
            val registrar = registrar { HttpStatusCode.Forbidden to SECRET_REFUSED }

            val result = registrar.register()

            assertIs<AppResult.Failure>(result)
            assertEquals(2, requests.size)
            assertEquals(1, ids.renewals)
        }

    @Test
    fun `another failure keeps the id and the secret for the next try`() =
        runTest {
            secrets.kept[FIRST_ID] = SECRET
            val answers =
                listOf(
                    HttpStatusCode.TooManyRequests to """{"success":false,"error":{"code":"RATE_LIMITED","message":"m"}}""",
                    HttpStatusCode.Forbidden to """{"success":false,"error":{"code":"FORBIDDEN","message":"m"}}""",
                )
            for ((status, body) in answers) {
                requests.clear()
                val result = registrar { status to body }.register()
                val error = assertIs<AppError.Api>(assertIs<AppResult.Failure>(result).error)
                assertEquals(status.value, error.httpStatus)
                assertEquals(1, requests.size)
            }
            assertEquals(0, ids.renewals, "a FORBIDDEN without the reason is not a refused secret")
            assertEquals(SECRET, secrets.kept[FIRST_ID])
        }

    @Test
    fun `start registers once the sign-in is read - again when the token arrives - coalescing what lands together`() =
        runTest {
            val push = Push()
            val arrivals = Channel<HttpRequestData>(Channel.UNLIMITED)
            val registrar =
                registrar(push) { request ->
                    arrivals.send(request)
                    NOT_ISSUED.ok()
                }
            registrar.start(backgroundScope)

            runCurrent()
            push.tokens.value = "fcm-token-1"
            advanceTimeBy(InstallRegistrar.COALESCE * 2)
            val first = arrivals.next()
            assertEquals("fcm-token-1", first.json()["token"]?.jsonPrimitive?.content, "the start-up registration waited for the token")

            push.tokens.value = "fcm-token-2"
            advanceTimeBy(InstallRegistrar.COALESCE * 2)
            assertEquals(
                "fcm-token-2",
                arrivals
                    .next()
                    .json()["token"]
                    ?.jsonPrimitive
                    ?.content,
                "a rotated token registers again",
            )
            assertEquals(2, requests.size, "nothing else was sent")
        }

    /** The next request the engine saw; the engine answers on its own thread, so this waits in real time. */
    private suspend fun Channel<HttpRequestData>.next(): HttpRequestData =
        withContext(Dispatchers.Default) { withTimeout(ARRIVAL_TIMEOUT_MS) { receive() } }

    private fun String.ok() = HttpStatusCode.OK to this

    private companion object {
        const val OVERLAP_WINDOW_MS = 50L
        const val ARRIVAL_TIMEOUT_MS = 5_000L
        const val FIRST_ID = "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e"
        const val SECOND_ID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"
        const val SECRET = "kq3VZ1x0Rm2b8yQe0n4tH6wXg9aJ5cLsP7dF2uK1oYi"
        const val ISSUED = """{"success":true,"data":{"installSecret":"$SECRET"},"error":null}"""
        const val NOT_ISSUED = """{"success":true,"data":{"installSecret":null},"error":null}"""
        const val SECRET_REFUSED =
            """{"success":false,"error":{"code":"FORBIDDEN","message":"Register this install again.","reason":"INSTALL_SECRET_INVALID"}}"""
    }
}
