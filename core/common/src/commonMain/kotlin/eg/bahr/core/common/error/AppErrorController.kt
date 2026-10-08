package eg.bahr.core.common.error

import eg.bahr.core.common.result.AppError
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

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
 * A [Channel], not a `SharedFlow`: with no collector attached (activity recreating, app in the
 * background) a `SharedFlow` without replay drops the event, while the channel buffers it for
 * the one host that consumes it.
 */
class AppErrorController {
    private val channel = Channel<AppError>(capacity = BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Consumed by the app's single error host. Each error is delivered once. */
    val errors: Flow<AppError> = channel.receiveAsFlow()

    /** Non-suspending, so a view model can call it from any branch without a scope. */
    fun show(error: AppError) {
        channel.trySend(error)
    }

    private companion object {
        /** More queued messages than this are stale by the time they would show; keep the newest. */
        const val BUFFER = 4
    }
}
