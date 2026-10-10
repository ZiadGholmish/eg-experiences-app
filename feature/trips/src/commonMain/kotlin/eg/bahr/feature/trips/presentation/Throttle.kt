package eg.bahr.feature.trips.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How long after a Retry further taps are ignored (M4-M6). A frustrated user taps Retry several times
 * in a row; without this each tap is a request. Long enough to cover a burst of taps, short enough
 * that a second deliberate retry after a fresh failure is not swallowed.
 */
internal const val RETRY_THROTTLE_MILLIS = 1_000L

/**
 * Leading-edge throttle: the first call runs at once, calls in the following [windowMillis] are
 * dropped. The window is a coroutine `delay` in [scope], so it follows the test scheduler's virtual
 * clock in tests. Called on the main thread only (taps), so it needs no lock.
 */
internal class Throttle(
    private val scope: CoroutineScope,
    private val windowMillis: Long = RETRY_THROTTLE_MILLIS,
) {
    private var window: Job? = null

    fun attempt(action: () -> Unit) {
        if (window?.isActive == true) return
        action()
        window = scope.launch { delay(windowMillis) }
    }
}

/**
 * [action] behind a [Throttle] that lives as long as the caller is composed: for a retry that only
 * the screen can run (a failed next page's, which `LazyPagingItems.retry` reloads on its own).
 */
@Composable
internal fun rememberThrottled(action: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val throttle = remember(scope) { Throttle(scope) }
    val current by rememberUpdatedState(action)
    return remember(throttle) { { throttle.attempt { current() } } }
}
