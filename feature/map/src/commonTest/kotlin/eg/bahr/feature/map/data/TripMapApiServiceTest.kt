package eg.bahr.feature.map.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.apiHttpClient
import eg.bahr.feature.map.model.TripMapDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/v1/trips/map` through the app's real client on a MockEngine, against a body captured from
 * the local api ([MapPayloads]): every field of `MapData` decodes, an older server's pin (required
 * fields only) still decodes, and an error envelope is a failure carrying its code.
 */
class TripMapApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun service(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        language: String = "en",
    ): TripMapApiService {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        return TripMapApiService(apiHttpClient(engine, ApiConfig(BASE_URL, isDebug = false)) { language })
    }

    private suspend fun map(body: String = MapPayloads.ENGLISH): TripMapDto {
        val result = service(body).tripMap()
        assertIs<AppResult.Success<TripMapDto>>(result)
        return result.data
    }

    @Test
    fun `asks for the map with the stored language`() =
        runTest {
            service(MapPayloads.ARABIC, language = "ar").tripMap()

            val request = requests.single()
            assertEquals("/api/v1/trips/map", request.url.encodedPath)
            assertEquals("ar", request.headers[HttpHeaders.AcceptLanguage])
        }

    @Test
    fun `a captured pin decodes field for field`() =
        runTest {
            val pin = map().pins.single { it.slug == "burullus-dawn" }

            assertEquals("Dawn on Lake Burullus", pin.title)
            assertEquals("Lake Burullus · Kafr El Sheikh", pin.subtitle)
            assertEquals(450L, pin.price.amount)
            assertEquals("EGP", pin.price.currencyCode)
            assertEquals("05:00 → 22:00", pin.durationLabel)
            assertEquals(0, pin.nights)
            assertEquals(31.503, pin.lat)
            assertEquals(30.804, pin.lng)
            assertEquals("Burg El Burullus", pin.placeName)
            assertEquals("on_the_boat", pin.category)
            assertEquals("primary", pin.tone)
            assertEquals("05:00", pin.departTime)
            assertEquals("0199c3a0-5eed-7000-8000-000000000201", pin.departurePointId)
            assertEquals(150, pin.distanceKm)
            val next = pin.nextDeparture!!
            assertEquals("0199c3a0-5eed-7000-8000-000000000601", next.id)
            assertEquals(LocalDate(2026, 10, 17), next.date)
            assertEquals(LocalDate(2026, 10, 17), next.returnDate)
            assertEquals(6, next.seatsRemaining)
            assertEquals(18, next.capacity)
            assertEquals(false, next.soldOut)
        }

    @Test
    fun `a sold-out multi-day pin keeps its nights and return date`() =
        runTest {
            val pin = map().pins.single { it.slug == "white-desert-overnight" }

            assertEquals(1, pin.nights)
            assertEquals("2 days · 1 night", pin.durationLabel)
            assertEquals(true, pin.nextDeparture?.soldOut)
            assertEquals(0, pin.nextDeparture?.seatsRemaining)
            assertEquals(LocalDate(2026, 10, 16), pin.nextDeparture?.returnDate)
        }

    @Test
    fun `departure points and legend decode`() =
        runTest {
            val data = map(MapPayloads.ARABIC)

            val cairo = data.departurePoints.single()
            assertEquals("0199c3a0-5eed-7000-8000-000000000201", cairo.id)
            assertEquals("موقف عبد المنعم رياض", cairo.placeName)
            assertEquals("القاهرة", cairo.city)
            assertEquals("القاهرة", cairo.governorate)
            assertEquals(30.0566, cairo.lat)
            assertEquals(31.2288, cairo.lng)
            assertEquals(listOf("on_the_boat", "birds", "murals", "beach", "night_trips"), data.legend.map { it.key })
            assertEquals("طيور", data.legend[1].label)
            assertEquals("flutter_dash", data.legend[1].icon)
            assertEquals("secondary", data.legend[1].tone)
        }

    @Test
    fun `an older server's pin with only the required fields still decodes`() =
        runTest {
            val pin = map(MapPayloads.MINIMAL).pins.single()

            assertNull(pin.subtitle)
            assertNull(pin.placeName)
            assertNull(pin.category)
            assertNull(pin.nextDeparture)
            assertNull(pin.departTime)
            assertNull(pin.departurePointId)
            assertNull(pin.distanceKm)
        }

    @Test
    fun `an error envelope is a failure with its code`() =
        runTest {
            val result = service(MapPayloads.NOT_FOUND, status = HttpStatusCode.NotFound).tripMap()

            val failure = assertIs<AppResult.Failure>(result)
            val error = assertIs<AppError.Api>(failure.error)
            assertEquals("NOT_FOUND", error.code)
            assertTrue(error.httpStatus == HttpStatusCode.NotFound.value)
        }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:8084/api/v1/"
    }
}
