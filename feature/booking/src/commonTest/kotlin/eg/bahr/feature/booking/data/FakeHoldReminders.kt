package eg.bahr.feature.booking.data

import eg.bahr.feature.booking.di.HoldReminder
import eg.bahr.feature.booking.di.HoldReminders
import eg.bahr.feature.booking.presentation.HoldDeadline
import eg.bahr.feature.booking.presentation.HoldReminderSchedule

/** Hand-written fake of the platform scheduler: what was scheduled and cancelled, in order. */
internal class FakeHoldReminders : HoldReminders {
    val scheduled = mutableListOf<HoldReminder>()
    val cancelled = mutableListOf<String>()

    override fun schedule(reminder: HoldReminder) {
        scheduled += reminder
    }

    override fun cancel(ref: String) {
        cancelled += ref
    }
}

/** Hand-written fake of the view models' side: each live read and each end, by hold reference. */
internal class RecordingHoldReminderSchedule : HoldReminderSchedule {
    val held = mutableListOf<Pair<String, HoldDeadline>>()
    val ended = mutableListOf<String>()

    override suspend fun onHeld(
        ref: String,
        deadline: HoldDeadline,
        tripTitle: String?,
        partySize: Int?,
    ) {
        held += ref to deadline
    }

    override fun onEnded(ref: String) {
        ended += ref
    }
}
