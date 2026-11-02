package eg.bahr.feature.notifications.data

import eg.bahr.feature.notifications.model.NotificationDto
import eg.bahr.feature.notifications.model.NotificationTargetDto

/** Inbox items shaped as openapi's `Notification`, at fixed times (the tests' clock is [NOW], Cairo is UTC+3). */
internal object NotificationFixtures {
    /** Sat 24 Oct 2026, 12:00 in Cairo. */
    const val NOW = "2026-10-24T09:00:00Z"

    fun item(
        id: String,
        type: String = "booking_confirmed",
        createdAt: String = "2026-10-24T08:48:00Z",
        readAt: String? = null,
        target: NotificationTargetDto = NotificationTargetDto(kind = "booking", id = "BRL-7K4M2X9P"),
        title: String = "Booking confirmed · BRL-7K4M2X9P",
        body: String = "Dawn on Lake Burullus · Sat 17 Oct · 2 people",
    ) = NotificationDto(id = id, type = type, title = title, body = body, createdAt = createdAt, readAt = readAt, target = target)

    fun seatOpened(
        id: String = "n-seat",
        createdAt: String = "2026-10-24T08:48:00Z",
    ) = item(
        id = id,
        type = "waitlist_seat_opened",
        createdAt = createdAt,
        target =
            NotificationTargetDto(
                kind = "trip_date",
                id = "0199c3a0-5eed-7000-8000-000000000601",
                link = "https://bahr.example/t/burullus-dawn?date=2026-10-31",
                tripSlug = "burullus-dawn",
                departureId = "0199c3a0-5eed-7000-8000-000000000601",
                date = "2026-10-31",
            ),
        title = "A seat opened on a trip you're waiting for",
        body = "Dawn on Lake Burullus · first to book gets it",
    )
}
