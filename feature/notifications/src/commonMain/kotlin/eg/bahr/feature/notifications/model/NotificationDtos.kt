package eg.bahr.feature.notifications.model

import kotlinx.serialization.Serializable

/**
 * openapi `Notification` (M3-B9; the guest inbox's too, M3-B12). [type] and [NotificationTargetDto.kind] stay strings:
 * the contract adds values over time and says to treat an unknown one as a plain item, which an enum would turn into a
 * failed page.
 */
@Serializable
internal data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: String,
    /** Null while unread. */
    val readAt: String? = null,
    val target: NotificationTargetDto,
)

/** openapi `NotificationTarget`: where a tap goes. */
@Serializable
internal data class NotificationTargetDto(
    val kind: String,
    val id: String,
    val link: String? = null,
    /** `trip_date` and `trip` only. */
    val tripSlug: String? = null,
    /** `trip_date` only (equals [id]). */
    val departureId: String? = null,
    /** `trip_date` only, ISO. */
    val date: String? = null,
)

/** openapi `UnreadCount`. */
@Serializable
internal data class UnreadCountDto(
    val count: Int,
)
