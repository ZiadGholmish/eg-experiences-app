package eg.bahr.feature.notifications.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.auth.withInstallSecret
import eg.bahr.core.network.callApi
import eg.bahr.feature.notifications.model.NotificationDto
import eg.bahr.feature.notifications.model.UnreadCountDto
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

/** Whose inbox: the signed-in account's (`/me/notifications`) or, signed out, this install's guest inbox (M3-B12). */
internal enum class InboxOwner(
    val path: String,
) {
    Account("me/notifications"),
    Guest("devices/notifications"),
}

/**
 * The inbox routes (openapi tag `notifications`; M3-B9, M3-B12, M3-B16), the same shapes for both owners. The account's
 * carry the bearer token core:network adds while signed in; the guest's carry `X-Device-Id` and `X-Install-Secret`
 * instead ([withInstallSecret]), and no token is ever needed for them.
 */
internal class NotificationsApiService(
    private val client: HttpClient,
) {
    /** `listMyNotifications` / `listDeviceNotifications`: newest first. */
    suspend fun page(
        owner: InboxOwner,
        page: Int,
        size: Int = PAGE_SIZE,
    ): AppResult<PageDto<NotificationDto>> =
        callApi {
            client.get(owner.path) {
                proving(owner)
                parameter("page", page)
                parameter("size", size)
            }
        }

    /** `getMyUnreadNotificationCount` / `getDeviceUnreadNotificationCount`: the bell's badge. */
    suspend fun unreadCount(owner: InboxOwner): AppResult<UnreadCountDto> =
        callApi { client.get("${owner.path}/unread-count") { proving(owner) } }

    /** `markNotificationRead` (a tap) or, with [read] false, `markNotificationUnread` (M3-B16). */
    suspend fun mark(
        owner: InboxOwner,
        id: String,
        read: Boolean,
    ): AppResult<Unit> = callApi { client.post("${owner.path}/$id/${if (read) "read" else "unread"}") { proving(owner) } }

    /** `markAllNotificationsRead` ("Mark all as read"). */
    suspend fun markAllRead(owner: InboxOwner): AppResult<Unit> = callApi { client.post("${owner.path}/read-all") { proving(owner) } }

    /** `deleteNotification` (once the confirm sheet's Delete is tapped, M3-M22; M3-B16): 204 whether or not it was deleted before. */
    suspend fun delete(
        owner: InboxOwner,
        id: String,
    ): AppResult<Unit> = callApi { client.delete("${owner.path}/$id") { proving(owner) } }

    /** `deleteReadNotifications` ("Clear read", M3-B16). `read=true` is required: a bare DELETE never empties an inbox. */
    suspend fun clearRead(owner: InboxOwner): AppResult<Unit> =
        callApi {
            client.delete(owner.path) {
                proving(owner)
                parameter("read", true)
            }
        }

    private fun HttpRequestBuilder.proving(owner: InboxOwner) {
        if (owner == InboxOwner.Guest) withInstallSecret()
    }

    companion object {
        /** The contract's default page size (1..50). */
        const val PAGE_SIZE = 20
    }
}
