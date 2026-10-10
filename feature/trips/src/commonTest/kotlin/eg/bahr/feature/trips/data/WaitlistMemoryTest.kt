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
}
