package eg.bahr.feature.booking.presentation

import eg.bahr.feature.booking.model.BookingCalendarDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** MV-M13: the server's `Booking.calendar` read as the event the platform's calendar screen is filled with. */
class AddToCalendarTest {
    @Test
    fun `Cairo times cut to the minute read as instants - winter and summer offsets`() {
        assertEquals(Instant.parse("2026-11-07T03:00:00Z"), parseOffsetDateTime("2026-11-07T05:00+02:00"))
        assertEquals(Instant.parse("2026-07-04T02:00:00Z"), parseOffsetDateTime("2026-07-04T05:00+03:00"))
    }

    @Test
    fun `with seconds - fractions and Z too`() {
        assertEquals(Instant.parse("2026-11-07T03:00:00Z"), parseOffsetDateTime("2026-11-07T05:00:00+02:00"))
        assertEquals(Instant.parse("2026-11-07T03:00:00.5Z"), parseOffsetDateTime("2026-11-07T05:00:00.5+02:00"))
        assertEquals(Instant.parse("2026-11-07T03:00:00Z"), parseOffsetDateTime("2026-11-07T03:00Z"))
    }

    @Test
    fun `not a date-time - or no offset - reads as nothing`() {
        assertNull(parseOffsetDateTime(null))
        assertNull(parseOffsetDateTime(""))
        assertNull(parseOffsetDateTime("tomorrow at five"))
        // A local time alone is not an instant: which zone it is in is not for the app to guess.
        assertNull(parseOffsetDateTime("2026-11-07T05:00"))
    }

    @Test
    fun `the event - its start and end - title and place`() {
        val event =
            BookingCalendarDto(
                start = "2026-11-07T05:00+02:00",
                end = "2026-11-07T22:00+02:00",
                title = "Dawn on Lake Burullus",
                location = "Abdel Moneim Riad, Cairo",
            ).toEvent()
        assertEquals(
            CalendarEvent(
                title = "Dawn on Lake Burullus",
                start = Instant.parse("2026-11-07T03:00:00Z"),
                end = Instant.parse("2026-11-07T20:00:00Z"),
                location = "Abdel Moneim Riad, Cairo",
            ),
            event,
        )
    }

    @Test
    fun `no return time - the moment the bus leaves - and an end not after the start is dropped`() {
        assertNull(BookingCalendarDto(start = "2026-11-07T05:00+02:00", title = "T").toEvent()?.end)
        assertNull(BookingCalendarDto(start = "2026-11-07T05:00+02:00", end = "2026-11-07T05:00+02:00", title = "T").toEvent()?.end)
        assertNull(BookingCalendarDto(start = "2026-11-07T05:00+02:00", end = "soon", title = "T").toEvent()?.end)
    }

    @Test
    fun `a start that does not read - no event - and a blank place is left out`() {
        assertNull(BookingCalendarDto(start = "soon", title = "T").toEvent())
        assertNull(BookingCalendarDto(start = "2026-11-07T05:00+02:00", title = "T", location = " ").toEvent()?.location)
    }
}
