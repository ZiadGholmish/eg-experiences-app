package eg.bahr.feature.host.data

import eg.bahr.core.catalog.data.WaitlistMemory
import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.FixedClock
import kotlin.time.Clock

/**
 * The view models' waiting-list memory over [store] (empty by default) at [clock] (fixed by default).
 * The fakes are core:testing's (M4-M7); this helper stays here because [WaitlistMemory] is core:catalog's.
 */
internal fun waitlistMemory(
    store: FakeWaitlistJoinsStore = FakeWaitlistJoinsStore(),
    clock: Clock = FixedClock(),
) = WaitlistMemory(store, clock)
