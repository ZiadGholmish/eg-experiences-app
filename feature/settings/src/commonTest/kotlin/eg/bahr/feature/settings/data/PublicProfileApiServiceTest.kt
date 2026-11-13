package eg.bahr.feature.settings.data

import eg.bahr.core.common.auth.AuthTokens
import eg.bahr.core.common.auth.DeviceIdSource
import eg.bahr.core.common.auth.SessionStore
import eg.bahr.core.common.auth.StoredSession
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.feature.settings.model.ProfileVisibilityDto
import eg.bahr.feature.settings.model.UpdateProfileVisibilityRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MP-M2 on the real client (MockEngine), payloads after openapi's `TravellerProfile` and `ProfileVisibility` (MP-B2):
 * a section switched off is absent (null), one on but empty is `[]`; a review keeps its trip; 404 comes back as
 * NOT_FOUND by its code; the switches read and PATCH with the bearer token, sending only the field changed.
 */
class PublicProfileApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun HttpRequestData.bodyText() = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private suspend fun api(answer: (HttpRequestData) -> Pair<HttpStatusCode, String>): PublicProfileApiService {
        val session = AuthSession(MemoryStore())
        session.signedIn(TOKENS, accountName = "Mona", profileComplete = true, providers = listOf("google"))
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
        return PublicProfileApiService(client)
    }

    @Test
    fun `a full profile - every section - the review's trip`() =
        runTest {
            val result = api { HttpStatusCode.OK to FULL }.traveller(PUBLIC_ID)

            val profile = assertIs<AppResult.Success<*>>(result).data as eg.bahr.feature.settings.model.TravellerProfileDto
            assertEquals("${TEST_BASE_URL}travellers/$PUBLIC_ID", requests.single().url.toString())
            assertEquals("Mona S.", profile.name)
            assertEquals(LocalDate(2026, 3, 1), profile.memberSince)
            assertEquals(listOf("dawn-burullus"), profile.pastTrips?.map { it.slug })
            assertNull(profile.pastTrips?.single()?.nextDeparture)
            assertNull(profile.pastTrips?.single()?.likedByMe)
            assertEquals(emptyList(), profile.likedTrips, "on but empty is []")
            val review = assertNotNull(profile.reviews).single()
            assertEquals("rosetta-day", review.trip.slug)
            assertEquals(4, review.review.rating)
            assertNull(review.review.authorPublicId)
        }

    @Test
    fun `sections switched off are absent`() =
        runTest {
            val result = api { HttpStatusCode.OK to BARE }.traveller(PUBLIC_ID)

            val profile = (result as AppResult.Success).data
            assertNull(profile.pastTrips)
            assertNull(profile.likedTrips)
            assertNull(profile.reviews)
        }

    @Test
    fun `404 is NOT_FOUND by its code`() =
        runTest {
            val result = api { HttpStatusCode.NotFound to NOT_FOUND }.traveller("not-a-real-id")

            assertEquals(ApiErrorCodes.NOT_FOUND, ((result as AppResult.Failure).error as AppError.Api).code)
        }

    @Test
    fun `the switches are read with the token - a change sends only its field`() =
        runTest {
            val api = api { HttpStatusCode.OK to VISIBILITY }

            val read = api.visibility()
            val changed = api.updateVisibility(UpdateProfileVisibilityRequest(isPublic = true))

            val expected = ProfileVisibilityDto(PUBLIC_ID, isPublic = true, showReviews = true)
            assertEquals(expected, (read as AppResult.Success).data)
            assertEquals(expected, (changed as AppResult.Success).data)
            val (get, patch) = requests
            assertEquals(HttpMethod.Get, get.method)
            assertEquals("${TEST_BASE_URL}me/profile-visibility", get.url.toString())
            assertEquals("Bearer at", get.headers[HttpHeaders.Authorization])
            assertEquals(HttpMethod.Patch, patch.method)
            val body = Json.parseToJsonElement(patch.bodyText()).jsonObject
            assertEquals(setOf("public"), body.keys, "absent fields stay as they are")
            assertTrue(body.getValue("public").toString() == "true")
        }

    private class MemoryStore : SessionStore {
        var session: StoredSession? = null

        override suspend fun read() = session

        override suspend fun write(session: StoredSession?) {
            this.session = session
        }
    }

    private companion object {
        const val PUBLIC_ID = "3f9c2a7e5b1d4c8e9a0f6b2d7c4e1a95"
        val TOKENS = AuthTokens("at", "2099-10-13T10:15:00+03:00", "rt", "2099-11-12T10:00:00+03:00")

        const val CARD =
            """{"slug":"dawn-burullus","title":"Dawn on Lake Burullus","durationLabel":"05:00 → 22:00",""" +
                """"price":{"amount":450,"currency":"EGP"},"likeCount":12}"""

        /** openapi `TravellerProfileEnvelope`: every switch on; no liked trips yet. */
        const val FULL =
            """{"success":true,"data":{"publicId":"$PUBLIC_ID","name":"Mona S.","memberSince":"2026-03-01",""" +
                """"pastTrips":[$CARD],"likedTrips":[],"reviews":[{"author":"Mona S.","dateISO":"2026-10-03","body":"Lovely",""" +
                """"rating":4,"verified":true,"trip":{"slug":"rosetta-day","title":"A day in Rosetta"}}]},"error":null}"""

        /** Every section switched off. */
        const val BARE =
            """{"success":true,"data":{"publicId":"$PUBLIC_ID","name":"Mona S.","memberSince":"2026-03-01"},"error":null}"""

        const val NOT_FOUND = """{"success":false,"data":null,"error":{"code":"NOT_FOUND","message":"x"}}"""

        const val VISIBILITY =
            """{"success":true,"data":{"publicId":"$PUBLIC_ID","public":true,"showPastTrips":false,"showLikes":false,""" +
                """"showReviews":true,"showFullNameAndPhoto":false},"error":null}"""
    }
}
