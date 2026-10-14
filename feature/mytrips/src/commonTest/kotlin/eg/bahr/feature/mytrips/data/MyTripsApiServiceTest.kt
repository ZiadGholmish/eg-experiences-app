package eg.bahr.feature.mytrips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiConfig
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.apiHttpClient
import eg.bahr.core.testing.TEST_BASE_URL
import eg.bahr.core.testing.jsonMockEngine
import eg.bahr.feature.mytrips.model.BookingStatus
import eg.bahr.feature.mytrips.model.MyBookingDto
import eg.bahr.feature.mytrips.model.MyWaitlistEntryDto
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** `listMyBookings` and `listMyWaitlist` on the real client, payloads shaped as openapi's examples. */
class MyTripsApiServiceTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(body: String) =
        MyTripsApiService(
            apiHttpClient(
                engine =
                    jsonMockEngine { request ->
                        requests += request
                        body
                    },
                config = ApiConfig(baseUrl = TEST_BASE_URL, isDebug = false),
                languageTagProvider = { "en" },
            ),
        )

    @Test
    fun `bookings ask for the list - page and size - and read the booking fields My trips shows`() =
        runTest {
            val result = api(BOOKING_PAGE).bookings(status = "upcoming", page = 1)

            val url = requests.single().url
            assertTrue(url.encodedPath.endsWith("/me/bookings"))
            assertEquals("upcoming", url.parameters["status"])
            assertEquals("1", url.parameters["page"])
            assertEquals("20", url.parameters["size"])
            assertIs<AppResult.Success<PageDto<MyBookingDto>>>(result)
            val booking = result.data.items.single()
            assertEquals("BRL-7K4M2X9P", booking.ref)
            assertEquals(BookingStatus.Confirmed, BookingStatus.of(booking.status))
            assertEquals("Dawn on Lake Burullus", booking.trip?.title)
            assertEquals("05:00", booking.departure?.timeLocal)
            assertEquals(900, booking.total?.amount)
        }

    @Test
    fun `the waiting list reads each entry's availability`() =
        runTest {
            val result = api(WAITLIST).waitlist()

            assertTrue(
                requests
                    .single()
                    .url.encodedPath
                    .endsWith("/me/waitlist"),
            )
            assertIs<AppResult.Success<List<MyWaitlistEntryDto>>>(result)
            val entry = result.data.single()
            assertTrue(entry.bookable)
            assertEquals(2, entry.seatsRemaining)
            assertEquals("burullus-dawn", entry.trip?.slug)
        }

    @Test
    fun `a status this build does not know reads as other - not a crash`() {
        assertEquals(BookingStatus.Other, BookingStatus.of("REFUNDED"))
    }

    private companion object {
        const val BOOKING_PAGE =
            """{"success":true,"data":{"items":[{"ref":"BRL-7K4M2X9P","status":"CONFIRMED","serverNow":"2026-10-14T10:00:00+03:00",""" +
                """"trip":{"slug":"burullus-dawn","title":"Dawn on Lake Burullus"},"date":"2026-10-24","dayLabel":"Sat 24 Oct",""" +
                """"departure":{"name":"Ramses Hilton","timeLocal":"05:00","arriveBy":"04:45"},"partySize":2,""" +
                """"total":{"amount":900,"currency":"EGP"},"method":"card"}],"page":1,"size":20,"totalItems":21,"totalPages":2}}"""

        const val WAITLIST =
            """{"success":true,"data":[{"id":"7d1f3e2a-5b6c-4d7e-8f9a-0b1c2d3e4f5a",""" +
                """"departureId":"2c9e7a1b-3d4f-4a5b-9c8d-7e6f5a4b3c2d",""" +
                """"date":"2026-10-24","dayLabel":"Sat 24 Oct","departTime":"05:00",""" +
                """"partySize":2,"locale":"en",""" +
                """"joinedAt":"2026-10-10T09:00:00+03:00","seatsRemaining":2,"soldOut":false,"bookable":true,""" +
                """"trip":{"slug":"burullus-dawn","title":"Dawn on Lake Burullus"}}]}"""
    }
}
