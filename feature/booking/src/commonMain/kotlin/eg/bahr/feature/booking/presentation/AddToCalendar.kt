package eg.bahr.feature.booking.presentation

import androidx.compose.runtime.Composable
import eg.bahr.feature.booking.model.BookingCalendarDto
import kotlin.time.Instant

/**
 * The ticket's "Add to calendar" (MV-M13): the server's event (`Booking.calendar`, MV-B3) handed to the platform's own
 * calendar screen, where the traveller checks it and saves it. The app never writes to the calendar itself, so it asks
 * for no calendar permission: Android opens the calendar app's insert screen (`Intent.ACTION_INSERT`), iOS the system's
 * event editor (`EKEventEditViewController`, which since iOS 17 runs out of the app's process and needs no access).
 */
internal fun interface CalendarInserter {
    fun add(event: CalendarEvent)
}

/** The platform's [CalendarInserter], tied to the screen (Android starts an Activity, iOS presents over the root). */
@Composable
internal expect fun rememberCalendarInserter(): CalendarInserter

/** One event, its times already read: [end] null when the return time is not known (the event is then a moment). */
internal data class CalendarEvent(
    val title: String,
    val start: Instant,
    val end: Instant?,
    val location: String?,
)

/**
 * The served event as the platform takes it; null when its start does not read as a date-time, and then the ticket offers
 * no button (a button that does nothing would be worse). An end that does not read, or is not after the start, is
 * dropped, as the server itself does.
 */
internal fun BookingCalendarDto.toEvent(): CalendarEvent? {
    val begins = parseOffsetDateTime(start) ?: return null
    val ends = parseOffsetDateTime(end)?.takeIf { it > begins }
    return CalendarEvent(
        title = title,
        start = begins,
        end = ends,
        location = location?.takeIf { it.isNotBlank() },
    )
}

/**
 * An ISO-8601 date-time with an offset. The server sends the seconds today (`2026-11-27T06:00:00+02:00`); ISO 8601 also
 * allows them left out (`2026-11-27T06:00+02:00`), and the times are cut to the minute anyway, so that form is read too:
 * the seconds are put back before parsing again.
 */
internal fun parseOffsetDateTime(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
    val match = MINUTES_ONLY.matchEntire(value) ?: return null
    val (dateAndMinutes, offset) = match.destructured
    return runCatching { Instant.parse("$dateAndMinutes:00$offset") }.getOrNull()
}

/** `yyyy-MM-ddTHH:mm` then the offset (`Z`, `+03:00`, `-0500`). */
private val MINUTES_ONLY = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})([Zz]|[+-]\d{2}:?\d{2})$""")
