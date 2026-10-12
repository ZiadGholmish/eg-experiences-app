package eg.bahr.core.catalog.data

import eg.bahr.core.testing.FakeWaitlistJoinsStore
import eg.bahr.core.testing.FixedClock
import eg.bahr.core.testing.storedJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** [WaitlistMemory] over core:testing's fake store (moved here from feature:trips, M4-M7 / M4-M1c review #6). */
class WaitlistMemoryTest {
    @Test
    fun `today is the Cairo date - already Saturday there while it is still Friday in UTC`() {
        // Egypt is on summer time (UTC+3) until the last Thursday of October.
        val memory = waitlistMemory(clock = FixedClock(Instant.parse("2026-10-09T22:30:00Z")))

        assertEquals(LocalDate.parse("2026-10-10"), memory.today())
    }

    @Test
    fun `forgetPassed drops past dates and unreadable ones - today's date stays`() =
        runTest {
            val store =
                FakeWaitlistJoinsStore(
                    listOf(
                        storedJoin(departureId = "today", date = "2026-10-10"),
                        storedJoin(departureId = "yesterday", date = "2026-10-09"),
                        storedJoin(departureId = "garbled", date = "24/10/2026"),
                    ),
                )
            val memory = waitlistMemory(store)

            memory.forgetPassed()

            assertEquals(listOf("today"), store.joins.value.map { it.departureId })
            assertEquals(listOf("today"), memory.joins.first().map { it.departureId })
        }

    /**
     * M4-M13: the store's remove is also where an older build's phone joins are erased, so the prune
     * at start-up must reach it even when no date has passed.
     */
    @Test
    fun `forgetPassed reaches the store even with nothing to drop`() =
        runTest {
            val store = FakeWaitlistJoinsStore(listOf(storedJoin(departureId = "kept", date = "2026-10-24")))

            waitlistMemory(store).forgetPassed()

            assertEquals(listOf(emptySet<String>()), store.removals)
            assertEquals(listOf("kept"), store.joins.value.map { it.departureId })
        }

    /**
     * M4-M5 review S4: a well-formed but impossible date fails to parse with an IllegalArgumentException
     * on both platforms (the only exception `parsed()` catches), so the entry is left out of [WaitlistMemory.joins]
     * straight away, before any pruning, and `forgetPassed` removes it from the store.
     */
    @Test
    fun `a stored join with an impossible date is left out of joins and pruned`() =
        runTest {
            val store =
                FakeWaitlistJoinsStore(
                    listOf(
                        storedJoin(departureId = "kept", date = "2026-10-24"),
                        storedJoin(departureId = "impossible", date = "2026-13-40"),
                    ),
                )
            val memory = waitlistMemory(store)

            assertEquals(listOf("kept"), memory.joins.first().map { it.departureId }, "dropped from joins before pruning")
            assertEquals(listOf("kept", "impossible"), store.joins.value.map { it.departureId }, "still stored until pruned")

            memory.forgetPassed()

            assertEquals(listOf("kept"), store.joins.value.map { it.departureId })
        }
}

/** The memory over [store] (empty by default) at [clock] (fixed by default). */
internal fun waitlistMemory(
    store: FakeWaitlistJoinsStore = FakeWaitlistJoinsStore(),
    clock: Clock = FixedClock(),
) = WaitlistMemory(store, clock)
