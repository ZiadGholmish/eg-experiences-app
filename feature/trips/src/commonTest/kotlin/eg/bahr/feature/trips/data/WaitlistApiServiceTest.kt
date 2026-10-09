package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.trips.model.WaitlistRequest
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * `joinWaitlist` (`POST /departures/{departureId}/waitlist`) through the app's real client on a
 * MockEngine, answered as the contract (and the local api) does: 201 `{"success":true}`, or an
 * `ApiError` envelope.
 */
class WaitlistApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        status: HttpStatusCode,
    ): TripApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        return TripApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { "ar" })
    }

    private fun error(code: String) = """{"success":false,"error":{"code":"$code","message":"ignored by the app"}}"""

    private suspend fun join(
        body: String,
        status: HttpStatusCode,
    ) = service(body, status).joinWaitlist(DEPARTURE_ID, WaitlistRequest(phone = "+201001234567", partySize = 2))

    @Test
    fun `posts the phone and party to the departure's list without a locale`() =
        runTest {
            join("""{"success":true}""", HttpStatusCode.Created)

            val sent = requests.single()
            assertEquals(HttpMethod.Post, sent.method)
            assertEquals("/api/v1/departures/$DEPARTURE_ID/waitlist", sent.url.encodedPath)
            assertEquals("ar", sent.headers[HttpHeaders.AcceptLanguage])
            val body = Json.parseToJsonElement((sent.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            assertEquals("+201001234567", body["phone"]?.jsonPrimitive?.content)
            assertEquals("2", body["partySize"]?.jsonPrimitive?.content)
            // Left out, not sent as null: the contract defaults it to Accept-Language, and null is not in its enum.
            assertFalse("locale" in body)
        }

    @Test
    fun `a 201 with no data is a success with or without an explicit null`() =
        runTest {
            assertIs<AppResult.Success<Unit>>(join("""{"success":true}""", HttpStatusCode.Created))
            assertIs<AppResult.Success<Unit>>(join("""{"success":true,"data":null}""", HttpStatusCode.Created))
        }

    @Test
    fun `refusals keep their code`() =
        runTest {
            val cases =
                listOf(
                    HttpStatusCode.Conflict to ApiErrorCodes.CONFLICT,
                    HttpStatusCode.Conflict to ApiErrorCodes.DEPARTURE_NOT_OPEN,
                    HttpStatusCode.TooManyRequests to ApiErrorCodes.RATE_LIMITED,
                    HttpStatusCode.BadRequest to ApiErrorCodes.VALIDATION_FAILED,
                    HttpStatusCode.NotFound to ApiErrorCodes.NOT_FOUND,
                )
            cases.forEach { (status, code) ->
                val result = join(error(code), status)
                assertIs<AppResult.Failure>(result)
                val apiError = assertIs<AppError.Api>(result.error)
                assertEquals(code, apiError.code)
                assertEquals(status.value, apiError.httpStatus)
            }
        }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
        const val DEPARTURE_ID = "0199c3a0-5eed-7000-8000-000000000603"
    }
}
