package eg.bahr.feature.notifications.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.notifications.navigation.UnreadAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What outlives the notifications page (M3-M6), on the app's [scope]:
 * - the bell's unread count ([UnreadAlerts]): read when the inbox owner changes (sign-in, sign-out), after each
 *   registration that landed ([registrations]: a sign-in's moves the guest inbox into the account, openapi
 *   `registerDevice`), on foreground and push, and after each change the page makes; one read at a time, in order;
 * - the page's deferred deletes ([launch]): a swipe's DELETE goes when its undo has passed, which can be after the
 *   page has gone.
 */
internal class AlertsCenter(
    private val repository: NotificationsRepository,
    registrations: Flow<Unit>,
    private val scope: CoroutineScope,
) : UnreadAlerts {
    private val _count = MutableStateFlow<Int?>(null)
    override val count: StateFlow<Int?> = _count.asStateFlow()

    private val _arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * New items may be in the inbox: a push arrived, or a registration landed (a sign-in's moves the guest inbox into
     * the account). An open page reads its list again.
     */
    val arrivals: SharedFlow<Unit> = _arrivals.asSharedFlow()

    /** Pending reads, folded into the latest; one that follows a registration never registers again (no loop). */
    private val reads = Channel<Read>(Channel.CONFLATED)

    private enum class Read { Any, AfterRegistration }

    init {
        scope.launch { reads.receiveAsFlow().collect { read(registerOnRefusal = it == Read.Any) } }
        scope.launch {
            repository.session.map { it.inboxOwner }.distinctUntilChanged().collect { owner ->
                // One person's count is never shown to the next one on this phone, even for the time of a read.
                _count.value = null
                if (owner != null) refresh()
            }
        }
        scope.launch {
            registrations.collect {
                reads.trySend(Read.AfterRegistration)
                _arrivals.tryEmit(Unit)
            }
        }
    }

    override fun refresh() {
        reads.trySend(Read.Any)
    }

    override fun onPushReceived() {
        refresh()
        _arrivals.tryEmit(Unit)
    }

    /** What the page knows before the server answers: one more or one fewer unread, or none ([set]). */
    fun adjust(by: Int) = _count.update { count -> count?.let { (it + by).coerceAtLeast(0) } }

    fun set(count: Int) {
        _count.value = count
    }

    /** Work that must finish even if the page goes first (a delete after its undo). */
    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }

    private suspend fun read(registerOnRefusal: Boolean) {
        val owner = repository.session.value.inboxOwner ?: return
        val result = repository.unreadCount(owner, registerOnRefusal)
        // A sign-in or sign-out meanwhile: that answer is the other inbox's.
        if (result is AppResult.Success && repository.session.value.inboxOwner == owner) _count.value = result.data
    }
}
