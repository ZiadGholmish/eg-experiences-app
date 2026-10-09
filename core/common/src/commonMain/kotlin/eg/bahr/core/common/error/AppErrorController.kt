package eg.bahr.core.common.error

import eg.bahr.core.common.result.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * App-wide outlet for *transient* failures: ones the user should hear about while the screen
 * stays usable (a failed "load more", a refresh that did not land). One app-scoped instance is
 * injected into view models; the single error host mounted at the app root shows them.
 *
 * Why this and not a second flow on the view model: a view model exposes exactly one
 * `StateFlow<UiState>`. Putting a one-off message into that state needs a "consumed" flag and a
 * callback back into the VM; a per-VM event flow breaks the one-flow rule and dies with the
 * screen. Here the VM fires and forgets, and the message outlives navigation.
 *
 * Errors that *are* the screen's state (first page failed, the hold was refused) stay in the
 * UiState and render in place. Only use this when the screen keeps working without the user
 * acting on the error.
 *
 * **Acknowledged queue, not a channel.** An error stays at the head ([current]) until the host
 * calls [dismiss] after the snackbar has left the screen on its own. A host that is torn down
 * while showing it — a language switch re-keys the composition, an Activity is recreated — never
 * acknowledges it, so the next host shows the same message again, in the new language. (A channel
 * hands the element over before it is shown, so that message was lost.) The controller is
 * app-scoped, so this covers everything short of process death, which drops the queue.
 */
class AppErrorController {
    /** An error waiting for, or on, the screen. [id] tells two equal errors apart. */
    data class Pending(
        val id: Long,
        val error: AppError,
    )

    private val queue = MutableStateFlow(Queue())

    /** The error the host should be showing now, or null. Changes only on [show] into an empty queue and on [dismiss]. */
    val current: Flow<Pending?> = queue.map { it.items.firstOrNull() }.distinctUntilChanged()

    /** Non-suspending, so a view model can call it from any branch without a scope. */
    fun show(error: AppError) = queue.update { it.enqueue(error) }

    /** The host has finished showing the [Pending] with [id]; the next one, if any, becomes [current]. */
    fun dismiss(id: Long) = queue.update { queue -> queue.copy(items = queue.items.filterNot { it.id == id }) }

    private data class Queue(
        val nextId: Long = 0,
        val items: List<Pending> = emptyList(),
    ) {
        fun enqueue(error: AppError): Queue {
            val added = items + Pending(nextId, error)
            // Keep the head (it may be on screen); beyond that, keep only the newest.
            val kept = if (added.size <= BUFFER) added else listOf(added.first()) + added.drop(1).takeLast(BUFFER - 1)
            return Queue(nextId = nextId + 1, items = kept)
        }
    }

    private companion object {
        /** More queued messages than this are stale by the time they would show. */
        const val BUFFER = 4
    }
}
