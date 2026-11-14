package eg.bahr.feature.notifications.presentation

import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import eg.bahr.feature.notifications.data.LoadedNotification
import eg.bahr.feature.notifications.model.NotificationDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * What an item is about (openapi `NotificationType`): it picks the row's icon and tint (handoff §3's table, and our
 * picks for the types added since). An unknown type is [Other], a plain item.
 */
internal enum class AlertKind(
    val wire: String?,
) {
    BookingConfirmed("booking_confirmed"),
    SeatOpened("waitlist_seat_opened"),
    DateMoved("waitlist_date_moved"),
    HoldExpiring("hold_expiring"),
    TripReminder("trip_reminder"),
    ReviewApproved("review_approved"),
    ReviewRejected("review_rejected"),
    HostMessage("host_message"),
    BookingCancelled("booking_cancelled"),
    BookingRefund("booking_refund"),
    BookingChanged("booking_changed"),
    Broadcast("broadcast"),
    Other(null),
    ;

    companion object {
        fun of(type: String): AlertKind = entries.firstOrNull { it.wire == type } ?: Other
    }
}

/** Where a tap goes (openapi `NotificationTargetKind`), as far as the app has the page; null: nowhere beyond the item. */
internal sealed interface AlertTarget {
    /** The trip page; on [date] with it picked (`trip_date`: no seat is held), saying a place opened when [placeOpened]. */
    data class Trip(
        val slug: String,
        val date: String? = null,
        val placeOpened: Boolean = false,
    ) : AlertTarget

    /** The booking, by reference. */
    data class Booking(
        val ref: String,
    ) : AlertTarget

    /** My reviews (M3-M4, not built yet): the review's id. */
    data class Review(
        val id: String,
    ) : AlertTarget
}

/** Today or Earlier (handoff §3), by the date in Cairo. */
internal enum class InboxGroup { Today, Earlier }

/** One line of the page's list: a group's heading, or an item. */
internal sealed interface InboxEntry {
    /** The list key: unique across both kinds. */
    val key: String

    data class Header(
        val group: InboxGroup,
    ) : InboxEntry {
        override val key: String get() = "header-${group.name}"
    }

    data class Item(
        val id: String,
        val kind: AlertKind,
        val title: String,
        val body: String,
        val createdAt: Instant,
        val read: Boolean,
        val target: AlertTarget?,
    ) : InboxEntry {
        override val key: String get() = id
    }
}

/**
 * What the page changed before the server's list shows it (M3-M6): items [hidden] (deleted, or with their DELETE under
 * way; ids never come back, so they stay hidden), read or unread by hand ([read]), and "Mark all as read", which holds
 * for every item loaded by the read numbered [allReadUpTo] or before; a later read shows the server's state.
 */
internal data class InboxEdits(
    val hidden: Set<String> = emptySet(),
    val read: Map<String, Boolean> = emptyMap(),
    val allReadUpTo: Int = NO_READ,
) {
    companion object {
        /** No read has a number below 1: nothing is marked read wholesale. */
        const val NO_READ = 0
    }
}

internal val CAIRO: TimeZone = TimeZone.of("Africa/Cairo")

/** The list as shown: hidden items out, edits applied, a heading where Today turns into Earlier. */
internal fun PagingData<LoadedNotification>.toEntries(
    edits: InboxEdits,
    today: LocalDate,
): PagingData<InboxEntry> =
    filter { it.dto.id !in edits.hidden }
        .map { it.toItem(edits) }
        .insertSeparators { before: InboxEntry.Item?, after: InboxEntry.Item? ->
            val group = after?.let { groupOf(it.createdAt, today) } ?: return@insertSeparators null
            if (before == null || groupOf(before.createdAt, today) != group) InboxEntry.Header(group) else null
        }

internal fun groupOf(
    createdAt: Instant,
    today: LocalDate,
): InboxGroup = if (createdAt.toLocalDateTime(CAIRO).date == today) InboxGroup.Today else InboxGroup.Earlier

internal fun LoadedNotification.toItem(edits: InboxEdits): InboxEntry.Item {
    val kind = AlertKind.of(dto.type)
    val read = edits.read[dto.id] ?: (generation <= edits.allReadUpTo || dto.readAt != null)
    return InboxEntry.Item(
        id = dto.id,
        kind = kind,
        title = dto.title,
        body = dto.body,
        // A time that does not parse is a contract slip: the item still shows, among the earliest.
        createdAt = runCatching { Instant.parse(dto.createdAt) }.getOrDefault(Instant.DISTANT_PAST),
        read = read,
        target = dto.targetFor(kind),
    )
}

/**
 * The page a tap opens. Decisions (M3-M6, see the step report): a hold, and a cancelled or refunded booking, open
 * nothing, since the booking page the app has says "Booking confirmed" (and the hold screen needs the deadline and
 * total a target does not carry; no `hold_expiring` is sent yet); an unknown kind opens nothing (openapi).
 */
private fun NotificationDto.targetFor(kind: AlertKind): AlertTarget? =
    when (target.kind) {
        "trip_date" -> target.tripSlug?.let { AlertTarget.Trip(it, date = target.date, placeOpened = kind == AlertKind.SeatOpened) }
        "trip" -> AlertTarget.Trip(target.tripSlug ?: target.id)
        "booking" -> AlertTarget.Booking(target.id).takeUnless { kind == AlertKind.BookingCancelled || kind == AlertKind.BookingRefund }
        "hold" -> null
        "review" -> AlertTarget.Review(target.id)
        else -> null
    }
