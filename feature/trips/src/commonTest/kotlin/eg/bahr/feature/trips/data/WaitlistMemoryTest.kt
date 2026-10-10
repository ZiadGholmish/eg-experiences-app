package eg.bahr.feature.trips.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

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
