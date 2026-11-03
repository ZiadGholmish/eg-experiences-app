package eg.bahr.feature.booking.presentation

import eg.bahr.feature.booking.data.FakeHoldReminders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The hold reminder's rule (M3-M7) against a fake scheduler and a hand-moved clock: when it fires, from the server's
 * deadline, and when it is moved, skipped or cancelled.
 */
class HoldReminderPlannerTest {
    private class MovableClock(
        var now: Instant,
    ) : Clock {
        override fun now(): Instant = now
    }

    /** The phone's clock, hours off the server's on purpose. */
    private val clock = MovableClock(Instant.parse("2026-10-09T09:00:00Z"))
    private val scheduler = FakeHoldReminders()
    private val planner =
        HoldReminderPlanner(
            reminders = scheduler,
            texts = { minutes, trip, party -> "ends in $minutes" to "$trip · $party" },
            clock = clock,
        )

    /** The server says the hold has [left] by its own clock, answered just now on the device. */
    private fun deadline(left: Duration): HoldDeadline {
        val serverNow = Instant.parse("2026-10-09T19:27:30Z")
        return requireNotNull(HoldDeadline.from((serverNow + left).toString(), serverNow.toString(), clock.now))
    }

    @Test
    fun `fires three minutes before the server's deadline - whatever the device clock says`() =
        runTest {
            planner.onHeld("BRL-1", deadline(15.minutes), "Dawn on the lake", 2)

            val reminder = scheduler.scheduled.single()
            assertEquals("BRL-1", reminder.ref)
            assertEquals(12.minutes, reminder.fireIn)
            assertEquals(15.minutes, reminder.releasedIn)
            assertEquals("ends in 3", reminder.title)
            assertEquals("Dawn on the lake · 2", reminder.body)
        }

    @Test
    fun `less than the lead left - nothing is scheduled`() =
        runTest {
            planner.onHeld("BRL-1", deadline(2.minutes + 59.seconds), null, null)
            planner.onHeld("BRL-1", deadline(3.minutes), null, null)

            assertTrue(scheduler.scheduled.isEmpty())
            // Not cancelled either: a reminder already in the tray stays until the hold ends.
            assertTrue(scheduler.cancelled.isEmpty())
        }

    @Test
    fun `a read later moves it to the fresh deadline`() =
        runTest {
            planner.onHeld("BRL-1", deadline(15.minutes), null, null)
            clock.now += 5.minutes
            // The server's clock agrees the hold has 10 minutes left: still due at the same moment.
            planner.onHeld("BRL-1", deadline(10.minutes), null, null)

            assertEquals(listOf(12.minutes, 7.minutes), scheduler.scheduled.map { it.fireIn })
            assertTrue(scheduler.cancelled.isEmpty())
        }

    @Test
    fun `once due it is never scheduled a second time for the same hold`() =
        runTest {
            planner.onHeld("BRL-1", deadline(15.minutes), null, null)
            clock.now += 12.minutes
            // A read that, by a slightly older server clock, says a hair over three minutes are left.
            planner.onHeld("BRL-1", deadline(3.minutes + 1.seconds), null, null)

            assertEquals(1, scheduler.scheduled.size)
        }

    @Test
    fun `a new hold replaces the previous hold's reminder`() =
        runTest {
            planner.onHeld("BRL-1", deadline(15.minutes), null, null)
            planner.onHeld("BRL-2", deadline(15.minutes), null, null)

            assertEquals(listOf("BRL-1"), scheduler.cancelled)
            assertEquals(listOf("BRL-1", "BRL-2"), scheduler.scheduled.map { it.ref })
        }

    @Test
    fun `an ended hold's reminder is cancelled - and a later hold schedules afresh`() =
        runTest {
            planner.onHeld("BRL-1", deadline(15.minutes), null, null)
            planner.onEnded("BRL-1")
            planner.onHeld("BRL-2", deadline(15.minutes), null, null)

            // Cancelled once, by the end; the new hold has nothing left to replace.
            assertEquals(listOf("BRL-1"), scheduler.cancelled)
            assertEquals("BRL-2", scheduler.scheduled.last().ref)
        }

    @Test
    fun `texts that cannot be read skip the reminder - never the hold`() =
        runTest {
            val failing = HoldReminderPlanner(scheduler, texts = { _, _, _ -> error("no resources") }, clock = clock)

            failing.onHeld("BRL-1", deadline(15.minutes), null, null)

            assertTrue(scheduler.scheduled.isEmpty())
        }

    @Test
    fun `the lead is configurable`() =
        runTest {
            val early = HoldReminderPlanner(scheduler, texts = { m, _, _ -> "$m" to "" }, clock = clock, lead = 5.minutes)

            early.onHeld("BRL-1", deadline(15.minutes), null, null)

            assertEquals(10.minutes, scheduler.scheduled.single().fireIn)
            assertEquals("5", scheduler.scheduled.single().title)
        }
}
