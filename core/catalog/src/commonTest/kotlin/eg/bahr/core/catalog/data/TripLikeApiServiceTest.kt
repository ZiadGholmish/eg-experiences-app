package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.model.TripLikeDto
import eg.bahr.core.catalog.model.TripPageDto
import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.testing.FakeSessionStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MP-B1 through the app's real client on a MockEngine, with payloads from openapi.yaml: the heart's `PUT`/`DELETE
 * /me/likes/{tripSlug}`, the card's `likeCount`/`likedByMe`, and the token every trip read carries so the server can say
 * `likedByMe`.
 */
class TripLikeApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(
        signedIn: Boolean,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
    ) = apiHttpClient(
        engine =
            MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        config = ApiConfig(BASE_URL, isDebug = false),
        session = AuthSession(FakeSessionStore(if (signedIn) StoredSession(TOKENS) else null)),
        deviceIds = DeviceIdSource { "device-1" },
        languageTagProvider = { "en" },
    )

    @Test
    fun `a like is a PUT - with the token - and answers the trip's like state`() =
        runTest {
            val result = TripLikeApiService(client(signedIn = true, body = LIKED)).setLiked("burullus-dawn", liked = true)

            val request = requests.single()
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/v1/me/likes/burullus-dawn", request.url.encodedPath)
            assertEquals("Bearer at", request.headers[HttpHeaders.Authorization])
            val like = assertIs<AppResult.Success<*>>(result).data
            assertEquals(TripLikeDto("burullus-dawn", likeCount = 13, likedByMe = true), like)
        }

    @Test
    fun `taking it back is a DELETE`() =
        runTest {
            TripLikeApiService(client(signedIn = true, body = LIKED)).setLiked("burullus-dawn", liked = false)
            assertEquals(HttpMethod.Delete, requests.single().method)
        }

    @Test
    fun `a rate-limited toggle is a failure with its code`() =
        runTest {
            val result =
                TripLikeApiService(client(signedIn = true, status = HttpStatusCode.TooManyRequests, body = RATE_LIMITED))
                    .setLiked("burullus-dawn", liked = true)
            val error = assertIs<AppError.Api>(assertIs<AppResult.Failure>(result).error)
            assertEquals("RATE_LIMITED", error.code)
        }

    @Test
    fun `the trip list goes with the token when signed in - so the server can say likedByMe`() =
        runTest {
            TripListApiService(client(signedIn = true, body = EMPTY_PAGE)).listTrips()
            assertEquals("Bearer at", requests.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun `a guest's trip list goes without one`() =
        runTest {
            TripListApiService(client(signedIn = false, body = EMPTY_PAGE)).listTrips()
            assertNull(requests.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun `a card reads its likes - and an older server's card reads 0 and not liked`() {
        val json = Json { ignoreUnknownKeys = true }
        val withLikes = json.decodeFromString<TripCardDto>(CARD.removeSuffix("}") + ""","likeCount":7,"likedByMe":true}""")
        assertEquals(7, withLikes.likeCount)
        assertTrue(withLikes.likedByMe == true)
        val older = json.decodeFromString<TripCardDto>(CARD)
        assertEquals(0, older.likeCount)
        assertNull(older.likedByMe)
        assertFalse(TripLikes(signedIn = true).stateOf(older).liked)
    }

    @Test
    fun `a page of cards decodes with likes`() {
        val page =
            Json { ignoreUnknownKeys = true }.decodeFromString<TripPageDto>(
                """{"items":[${CARD.removeSuffix("}")},"likeCount":2}],"page":0,"size":20,"totalItems":1,"totalPages":1}""",
            )
        assertEquals(2, page.items.single().likeCount)
    }

    private companion object {
        const val BASE_URL = "http://test/api/v1/"
        val TOKENS = AuthTokens("at", "2099-01-01T00:00:00Z", "rt", "2099-02-01T00:00:00Z")

        // openapi `TripLikeEnvelope`.
        const val LIKED = """{"success":true,"data":{"slug":"burullus-dawn","likeCount":13,"likedByMe":true},"error":null}"""
        const val RATE_LIMITED =
            """{"success":false,"data":null,"error":{"code":"RATE_LIMITED","message":"Too many requests"}}"""
        const val EMPTY_PAGE = """{"success":true,"data":{"items":[],"page":0,"size":20,"totalItems":0,"totalPages":0}}"""

        // A card with only what the contract requires; the likes are added before its last brace.
        const val CARD =
            """{"slug":"burullus-dawn","title":"Dawn on Lake Burullus","durationLabel":"05:00 → 22:00","price":{"amount":45000,"currency":"EGP"}}"""
    }
}
