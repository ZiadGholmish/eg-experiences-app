package eg.bahr.feature.notifications.presentation

import eg.bahr.feature.notifications.data.LoadedNotification
import eg.bahr.feature.notifications.data.NotificationFixtures.item
import eg.bahr.feature.notifications.model.NotificationTargetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Each type's page (handoff §3's table, and the decisions for the types added since). */
class InboxEntriesTest {
    private fun target(
        type: String,
        kind: String,
        id: String = "BRL-7K4M2X9P",
        slug: String? = null,
    ) = LoadedNotification(item("n", type = type, target = NotificationTargetDto(kind = kind, id = id, tripSlug = slug)), 1)
        .toItem(InboxEdits())
        .target

    @Test
    fun `targets by kind - unknown types and kinds degrade to a plain item`() {
        assertEquals(AlertTarget.Booking("BRL-7K4M2X9P"), target("booking_confirmed", "booking"))
        assertEquals(AlertTarget.Booking("BRL-7K4M2X9P"), target("trip_reminder", "booking"))
        assertNull(target("hold_expiring", "hold"), "no hold page from an id, and the booking page says confirmed")
        assertNull(target("booking_cancelled", "booking"), "the booking page the app has says confirmed")
        assertNull(target("booking_refund", "booking"))
        assertEquals(AlertTarget.Trip("burullus-dawn"), target("broadcast", "trip", id = "burullus-dawn", slug = "burullus-dawn"))
        assertEquals(AlertTarget.Review("r-1"), target("review_approved", "review", id = "r-1"))
        assertNull(target("broadcast", "none", id = "b-1"))
        assertNull(target("booking_confirmed", "a_kind_from_later"))
        assertEquals(AlertKind.Other, AlertKind.of("a_type_from_later"))
    }

    @Test
    fun `a moved date opens on its new date without saying a place opened`() {
        val moved =
            LoadedNotification(
                item(
                    "n",
                    type = "waitlist_date_moved",
                    target =
                        NotificationTargetDto(
                            kind = "trip_date",
                            id = "d",
                            tripSlug = "burullus-dawn",
                            departureId = "d",
                            date = "2026-11-07",
                        ),
                ),
                1,
            ).toItem(InboxEdits())

        assertEquals(AlertTarget.Trip("burullus-dawn", date = "2026-11-07", placeOpened = false), moved.target)
    }

    @Test
    fun `edits - by hand wins over mark all - and mark all holds only for the reads it saw`() {
        val unread = item("n")
        val all = InboxEdits(allReadUpTo = 2)

        assertEquals(true, LoadedNotification(unread, 2).toItem(all).read)
        assertEquals(false, LoadedNotification(unread, 3).toItem(all).read, "a later read shows the server's state")
        assertEquals(false, LoadedNotification(unread, 2).toItem(all.copy(read = mapOf("n" to false))).read)
    }
}
