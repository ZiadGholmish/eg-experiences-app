package eg.bahr.feature.notifications.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * The app's long-lived scope in a test: on the test's scheduler, so `advanceUntilIdle` runs it (it skips
 * `backgroundScope`'s work), but not a child of the test, which would then wait for its endless collectors.
 */
internal fun TestScope.appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
