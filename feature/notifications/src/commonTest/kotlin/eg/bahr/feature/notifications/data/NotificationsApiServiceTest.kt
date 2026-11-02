package eg.bahr.feature.notifications.data

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.InstallSecretStore
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.feature.notifications.model.NotificationDto
import eg.bahr.feature.notifications.model.UnreadCountDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inbox routes (openapi tag `notifications`) through the real client on a MockEngine, payloads shaped as openapi's
 * `NotificationPageEnvelope` / `UnreadCountEnvelope`: the account's carry the bearer token, the guest's the install's
 * id and secret; a refused secret registers the install again and is sent once more, never twice.
 */
class NotificationsApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private class Sessions(
        var session: StoredSession? = null,
    ) : SessionStore {
        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    private val secrets =
        object : InstallSecretStore {
            override suspend fun read(deviceId: String) = SECRET.takeIf { deviceId == DEVICE_ID }

            override suspend fun write(
                deviceId: String,
                secret: String?,
            ) = Unit
        }

    private fun api(
        signedIn: Boolean = false,
        answer: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): Pair<NotificationsApiService, AuthSession> {
        val tokens = AuthTokens("at", "2099-01-01T00:00:00Z", "rt", "2099-02-01T00:00:00Z")
        val session = AuthSession(Sessions(if (signedIn) StoredSession(tokens) else null))
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
                deviceIds = DeviceIdSource { DEVICE_ID },
                installSecrets = secrets,
                languageTagProvider = { "en" },
            )
        return NotificationsApiService(client) to session
    }

    @Test
    fun `the account's page - with its token - read field for field`() =
        runTest {
            val (api, session) = api(signedIn = true) { HttpStatusCode.OK to PAGE }
            session.load()

            val result = api.page(InboxOwner.Account, page = 1)

            val sent = requests.single()
            assertTrue(sent.url.encodedPath.endsWith("/api/v1/me/notifications"))
            assertEquals("1", sent.url.parameters["page"])
            assertEquals("20", sent.url.parameters["size"])
            assertEquals("Bearer at", sent.headers[HttpHeaders.Authorization])
            assertNull(sent.headers["X-Install-Secret"], "the account's inbox needs no install secret")
            val page = assertIs<AppResult.Success<*>>(result).data as PageDto<*>
            val (first, second) = page.items.map { it as NotificationDto }
            assertEquals("waitlist_seat_opened", first.type)
            assertEquals("trip_date", first.target.kind)
            assertEquals("burullus-dawn", first.target.tripSlug)
            assertEquals("2026-10-31", first.target.date)
            assertNull(first.readAt)
            assertEquals("2026-10-24T08:00:00Z", second.readAt)
            assertEquals("a_type_from_later", second.type, "an unknown type still reads")
            assertEquals(2, page.totalPages)
        }

    @Test
    fun `the guest inbox goes with the install's id and secret - and every action has its route`() =
        runTest {
            val (api) =
                api { request ->
                    (
                        if (request.method ==
                            HttpMethod.Get
                        ) {
                            HttpStatusCode.OK to COUNT
                        } else {
                            HttpStatusCode.NoContent to ""
                        }
                    )
                }

            val count = api.unreadCount(InboxOwner.Guest)
            api.mark(InboxOwner.Guest, ID, read = true)
            api.mark(InboxOwner.Guest, ID, read = false)
            api.markAllRead(InboxOwner.Guest)
            api.delete(InboxOwner.Guest, ID)
            api.clearRead(InboxOwner.Guest)

            assertEquals(3, (assertIs<AppResult.Success<*>>(count).data as UnreadCountDto).count)
            assertEquals(
                listOf(
                    "GET /api/v1/devices/notifications/unread-count",
                    "POST /api/v1/devices/notifications/$ID/read",
                    "POST /api/v1/devices/notifications/$ID/unread",
                    "POST /api/v1/devices/notifications/read-all",
                    "DELETE /api/v1/devices/notifications/$ID",
                    "DELETE /api/v1/devices/notifications",
                ),
                requests.map { "${it.method.value} ${it.url.encodedPath}" },
            )
            assertEquals("true", requests.last().url.parameters["read"], "openapi ClearReadOnly: a bare DELETE never empties it")
            requests.forEach {
                assertEquals(DEVICE_ID, it.headers["X-Device-Id"])
                assertEquals(SECRET, it.headers["X-Install-Secret"])
                assertNull(it.headers[HttpHeaders.Authorization])
            }
        }

    @Test
    fun `a refused secret registers the install again and is sent once more - never twice`() =
        runTest {
            var refusals = 1
            val (api, session) = api { if (refusals-- > 0) HttpStatusCode.Forbidden to REFUSED else HttpStatusCode.OK to COUNT }
            var registrations = 0
            val repository = DefaultNotificationsRepository(api, session) { AppResult.Success(Unit).also { registrations++ } }

            assertEquals(AppResult.Success(3), repository.unreadCount(InboxOwner.Guest))
            assertEquals(1, registrations)
            assertEquals(2, requests.size)

            refusals = Int.MAX_VALUE
            requests.clear()
            assertIs<AppResult.Failure>(repository.unreadCount(InboxOwner.Guest))
            assertEquals(2, registrations, "one registration per call")
            assertEquals(2, requests.size, "the refusal after it is the answer: no loop")

            requests.clear()
            assertIs<AppResult.Failure>(repository.unreadCount(InboxOwner.Guest, registerOnRefusal = false))
            assertEquals(2, registrations, "a read after a registration does not register again")
            assertEquals(1, requests.size)
        }

    private companion object {
        const val DEVICE_ID = "3f2b1c4e-5d6a-4b7c-8d9e-0f1a2b3c4d5e"
        const val SECRET = "kq3VZ1x0Rm2b8yQe0n4tH6wXg9aJ5cLsP7dF2uK1oYi"
        const val ID = "0199d1e2-7a10-7000-8000-00000000a001"
        const val COUNT = """{"success":true,"data":{"count":3},"error":null}"""
        const val REFUSED =
            """{"success":false,"error":{"code":"FORBIDDEN","message":"Register this install again.","reason":"INSTALL_SECRET_INVALID"}}"""
        const val PAGE = """{"success":true,"data":{"items":[
            {"id":"0199d1e2-7a10-7000-8000-00000000a001","type":"waitlist_seat_opened","title":"A seat opened on a trip you're waiting for",
             "body":"Dawn on Lake Burullus · first to book gets it","createdAt":"2026-10-24T08:48:00Z","readAt":null,
             "target":{"kind":"trip_date","id":"0199c3a0-5eed-7000-8000-000000000601","link":"https://bahr.example/t/burullus-dawn?date=2026-10-31",
                       "tripSlug":"burullus-dawn","departureId":"0199c3a0-5eed-7000-8000-000000000601","date":"2026-10-31"}},
            {"id":"0199d1e2-7a10-7000-8000-00000000a002","type":"a_type_from_later","title":"Something new","body":"Shown as a plain item",
             "createdAt":"2026-10-23T08:00:00Z","readAt":"2026-10-24T08:00:00Z","target":{"kind":"none","id":"b-1"}}
            ],"page":1,"size":20,"totalItems":22,"totalPages":2},"error":null}"""
    }
}
