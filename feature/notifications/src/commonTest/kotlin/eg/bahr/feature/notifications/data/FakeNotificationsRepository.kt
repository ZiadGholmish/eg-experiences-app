package eg.bahr.feature.notifications.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.PageDto
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.notifications.model.NotificationDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The inbox in memory, per owner: [inboxes] are what the server holds (newest first), [calls] every action sent, by
 * name. [loaded] is what [loadSession] makes the session (the store's answer). Each action applies to the inbox as the
 * server would, unless [fail] answers it.
 */
internal class FakeNotificationsRepository(
    private val loaded: SessionState = SessionState.SignedOut,
    inboxes: Map<InboxOwner, List<NotificationDto>> = emptyMap(),
) : NotificationsRepository {
    val state = MutableStateFlow<SessionState>(SessionState.Unknown)
    override val session: StateFlow<SessionState> = state

    val inboxes = inboxes.mapValues { it.value.toMutableList() }.toMutableMap()
    val calls = mutableListOf<String>()
    var pageReads = 0

    /** An action's answer instead of the server's: by name ("mark", "delete", "markAllRead", "clearRead", "page"). */
    var fail: Map<String, AppResult.Failure> = emptyMap()

    override suspend fun loadSession() {
        state.value = loaded
    }

    override suspend fun page(
        owner: InboxOwner,
        page: Int,
    ): AppResult<PageDto<NotificationDto>> {
        pageReads++
        fail["page"]?.let { return it }
        val all = inbox(owner)
        val items = all.drop(page * PAGE).take(PAGE)
        return AppResult.Success(PageDto(items, page, PAGE, all.size.toLong(), (all.size + PAGE - 1) / PAGE))
    }

    override suspend fun unreadCount(
        owner: InboxOwner,
        registerOnRefusal: Boolean,
    ): AppResult<Int> = AppResult.Success(inbox(owner).count { it.readAt == null })

    override suspend fun mark(
        owner: InboxOwner,
        id: String,
        read: Boolean,
    ): AppResult<Unit> =
        act("mark", "$owner:$id:$read") {
            replace(owner, id) { it.copy(readAt = if (read) READ_AT else null) }
        }

    override suspend fun markAllRead(owner: InboxOwner) =
        act("markAllRead", "$owner") {
            inboxes[owner] = inbox(owner).map { it.copy(readAt = it.readAt ?: READ_AT) }.toMutableList()
        }

    override suspend fun delete(
        owner: InboxOwner,
        id: String,
    ) = act("delete", "$owner:$id") { inbox(owner).removeAll { it.id == id } }

    override suspend fun clearRead(owner: InboxOwner) = act("clearRead", "$owner") { inbox(owner).removeAll { it.readAt != null } }

    private fun inbox(owner: InboxOwner) = inboxes.getOrPut(owner) { mutableListOf() }

    private fun replace(
        owner: InboxOwner,
        id: String,
        change: (NotificationDto) -> NotificationDto,
    ) {
        val inbox = inbox(owner)
        val at = inbox.indexOfFirst { it.id == id }
        if (at >= 0) inbox[at] = change(inbox[at])
    }

    private fun act(
        name: String,
        args: String,
        change: () -> Unit,
    ): AppResult<Unit> {
        calls += "$name($args)"
        fail[name]?.let { return it }
        change()
        return AppResult.Success(Unit)
    }

    companion object {
        const val PAGE = 20
        const val READ_AT = "2026-10-24T09:00:00Z"
    }
}
