package eg.bahr.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import eg.bahr.core.common.error.AppErrorController
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.notifications.data.AlertsCenter
import eg.bahr.feature.notifications.data.InboxOwner
import eg.bahr.feature.notifications.data.LoadedNotification
import eg.bahr.feature.notifications.data.NotificationsApiService
import eg.bahr.feature.notifications.data.NotificationsPagingSource
import eg.bahr.feature.notifications.data.NotificationsRepository
import eg.bahr.feature.notifications.data.inboxOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** A swiped item waiting out its undo (M3-M6): deleted on the server only once the snackbar has gone. */
internal data class PendingDelete(
    val id: String,
    val owner: InboxOwner,
    val wasUnread: Boolean,
)

internal data class NotificationsUiState(
    val session: SessionState = SessionState.Unknown,
    /** What the rows' "12 min" counts from: the time of the last read of the list. */
    val now: Instant = Instant.DISTANT_PAST,
    /** The undo snackbar's item. */
    val pendingDelete: PendingDelete? = null,
    /** The "Clear read" confirmation is up; [clearingRead] while its request is under way. */
    val confirmClearRead: Boolean = false,
    val clearingRead: Boolean = false,
)

/**
 * The notifications page (M3-M6, account handoff §3; pushed from Home's bell since M3-M6a). Newest first in Today and
 * Earlier groups, the account's inbox signed in and this install's guest inbox signed out (M3-B12), page by page
 * ([notifications]). Read on open, on each return, and whenever a push arrives or a registration lands ([AlertsCenter.arrivals]); a sign-in or
 * sign-out starts it over on the other inbox.
 *
 * Every change shows at once and the bell's count follows it ([AlertsCenter]), then the server's answer settles it: a
 * failure undoes the change and says so (the app's error snackbar). A swipe hides the item and offers Undo; the DELETE
 * goes when the snackbar has gone, or a next swipe, or the page closes, on the app's scope so it is never lost
 * (deletion is a tombstone, so there is nothing to restore it with afterwards).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NotificationsViewModel(
    private val repository: NotificationsRepository,
    private val center: AlertsCenter,
    private val errors: AppErrorController,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState(now = clock.now()))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val edits = MutableStateFlow(InboxEdits())

    /** The list's current read, so a refresh can ask it to start over; numbered so edits know what they cover. */
    private var source: NotificationsPagingSource? = null
    private var generation = InboxEdits.NO_READ

    /** The inbox on show (Unknown: none yet), as a paged list of headings and items. */
    val notifications: Flow<PagingData<InboxEntry>> =
        repository.session
            .map { it.inboxOwner }
            .distinctUntilChanged()
            .flatMapLatest { owner ->
                // Another person's inbox now: nothing they changed applies, and an undo window closes on the old one.
                commitPendingDelete()
                edits.value = InboxEdits()
                // The list starts empty and loading on each owner: Paging would keep drawing the previous inbox until the
                // new one's first page lands, and for ever if that page fails (M3-M0 review #5's rule).
                val cleared = PagingData.empty<LoadedNotification>(LOADING)
                if (owner == null) {
                    flowOf(cleared)
                } else {
                    flow {
                        emit(cleared)
                        emitAll(
                            Pager(PAGING) {
                                NotificationsPagingSource(repository, owner, ++generation, onFirstPage = ::listRead).also { source = it }
                            }.flow,
                        )
                    }
                }
            }.cachedIn(viewModelScope)
            .combine(edits) { page, edits -> page.toEntries(edits, today = clock.now().toLocalDateTime(CAIRO).date) }
            .cachedIn(viewModelScope)

    init {
        repository.session
            .onEach { session -> _uiState.update { it.copy(session = session) } }
            .launchIn(viewModelScope)
        viewModelScope.launch { repository.loadSession() }
        // A push while the page is open: the new item shows at the top.
        center.arrivals.onEach { refresh() }.launchIn(viewModelScope)
        center.refresh()
    }

    /** Read the list again from the top (back on the page, Try again), and the bell's count with it. */
    fun refresh() {
        source?.invalidate()
        center.refresh()
    }

    /** A tap: the item is read (the caller opens its page). */
    fun open(item: InboxEntry.Item) {
        if (!item.read) setRead(item, read = true)
    }

    /** The row's menu: "Mark as read" / "Mark as unread". */
    fun setRead(
        item: InboxEntry.Item,
        read: Boolean,
    ) {
        if (item.read == read) return
        val owner = owner() ?: return
        edits.update { it.copy(read = it.read + (item.id to read)) }
        center.adjust(if (read) -1 else 1)
        viewModelScope.launch {
            when (val result = repository.mark(owner, item.id, read)) {
                is AppResult.Success -> Unit
                is AppResult.Failure ->
                    if (result.error.isNotFound()) {
                        // Deleted meanwhile (on another phone): it goes from this list too.
                        edits.update { it.copy(hidden = it.hidden + item.id) }
                    } else {
                        edits.update { e -> if (e.read[item.id] == read) e.copy(read = e.read - item.id) else e }
                        errors.show(result.error)
                    }
            }
            center.refresh()
        }
    }

    /** A swipe (or the menu's Delete): hidden now, deleted once its undo has passed. */
    fun delete(item: InboxEntry.Item) {
        val owner = owner() ?: return
        commitPendingDelete()
        edits.update { it.copy(hidden = it.hidden + item.id) }
        if (!item.read) center.adjust(-1)
        _uiState.update { it.copy(pendingDelete = PendingDelete(item.id, owner, wasUnread = !item.read)) }
    }

    /** The snackbar's Undo: the item is back, and nothing is sent. */
    fun undoDelete(id: String) {
        val pending = _uiState.value.pendingDelete?.takeIf { it.id == id } ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        edits.update { it.copy(hidden = it.hidden - id) }
        if (pending.wasUnread) center.adjust(1)
    }

    /** The snackbar went without an Undo. */
    fun commitDelete(id: String) {
        if (_uiState.value.pendingDelete?.id == id) commitPendingDelete()
    }

    /** "Mark all as read": every item loaded so far shows read, and the bell clears. */
    fun markAllRead() {
        val owner = owner() ?: return
        val before = edits.value
        val countBefore = center.count.value
        edits.update { it.copy(read = emptyMap(), allReadUpTo = generation) }
        center.set(0)
        viewModelScope.launch {
            val result = repository.markAllRead(owner)
            when (result) {
                // A read that started before the server marked them would show them unread: read again.
                is AppResult.Success -> source?.invalidate()
                is AppResult.Failure -> {
                    edits.value = before
                    countBefore?.let(center::set)
                    errors.show(result.error)
                }
            }
            center.refresh()
        }
    }

    fun askClearRead() {
        _uiState.update { it.copy(confirmClearRead = true) }
    }

    fun dismissClearRead() {
        _uiState.update { if (it.clearingRead) it else it.copy(confirmClearRead = false) }
    }

    /**
     * "Clear read", confirmed: deletes every read item on the server (it cannot be undone, hence the sheet), then reads
     * the list again. Unread items stay, so the count does not change.
     */
    fun clearRead() {
        val owner = owner() ?: return
        _uiState.update { it.copy(clearingRead = true) }
        viewModelScope.launch {
            val result = repository.clearRead(owner)
            _uiState.update { it.copy(clearingRead = false, confirmClearRead = false) }
            when (result) {
                is AppResult.Success -> source?.invalidate()
                is AppResult.Failure -> errors.show(result.error)
            }
        }
    }

    override fun onCleared() {
        // The page closed inside an undo window: the delete still goes (on the app's scope).
        commitPendingDelete()
    }

    private fun commitPendingDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        center.launch {
            val result = repository.delete(pending.owner, pending.id)
            // Already gone is what was asked for.
            if (result is AppResult.Failure && !result.error.isNotFound()) {
                edits.update { it.copy(hidden = it.hidden - pending.id) }
                errors.show(result.error)
            }
            center.refresh()
        }
    }

    private fun listRead() {
        _uiState.update { it.copy(now = clock.now()) }
    }

    private fun owner(): InboxOwner? = repository.session.value.inboxOwner

    private fun AppError.isNotFound(): Boolean = this is AppError.Api && code == ApiErrorCodes.NOT_FOUND

    private companion object {
        val LOADING = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false))

        val PAGING =
            PagingConfig(
                pageSize = NotificationsApiService.PAGE_SIZE,
                initialLoadSize = NotificationsApiService.PAGE_SIZE,
                enablePlaceholders = false,
            )
    }
}
