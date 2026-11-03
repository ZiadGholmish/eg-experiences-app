package eg.bahr.feature.booking.presentation

import co.touchlab.kermit.Logger
import eg.bahr.core.localization.generated.resources.Res
import eg.bahr.core.localization.generated.resources.booking_party_count
import eg.bahr.core.localization.generated.resources.hold_reminder_body
import eg.bahr.core.localization.generated.resources.hold_reminder_body_plain
import eg.bahr.core.localization.generated.resources.hold_reminder_title
import eg.bahr.feature.booking.di.HoldReminder
import eg.bahr.feature.booking.di.HoldReminders
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * What the hold screens tell the "your hold is about to expire" reminder (M3-M7). The view models call it; the rule of
 * when to show it lives in one place ([HoldReminderPlanner]), and their tests use a recording fake.
 */
internal interface HoldReminderSchedule {
    /**
     * The server has just said [ref] is held until [deadline] (built from its `holdExpiresAt` and `serverNow`): placed,
     * or read again. Schedules the reminder, or moves it to the fresh deadline.
     */
    suspend fun onHeld(
        ref: String,
        deadline: HoldDeadline,
        tripTitle: String?,
        partySize: Int?,
    )

    /** [ref] is over: paid, released, expired, or forgotten as unknown. Its reminder goes. */
    fun onEnded(ref: String)
}

/** Schedules nothing: the view models' default, for tests that do not look at reminders. */
internal object NoHoldReminderSchedule : HoldReminderSchedule {
    override suspend fun onHeld(
        ref: String,
        deadline: HoldDeadline,
        tripTitle: String?,
        partySize: Int?,
    ) = Unit

    override fun onEnded(ref: String) = Unit
}

/** The reminder's words, in the app's language. A seam for tests, which have no string resources to read. */
internal fun interface HoldReminderTexts {
    suspend fun texts(
        minutes: Int,
        tripTitle: String?,
        partySize: Int?,
    ): Pair<String, String>
}

/**
 * The prototype's `hold_expiring` item: "Your seat hold ends in 3 min" over "trip · 2 people · pay before the seats are
 * released". Read outside composition: compose-resources follow the process locale, which the app keeps on its own
 * language (core:localization's `LocalAppLocale`), so this is the app's language, not the device's.
 */
internal object ResourceHoldReminderTexts : HoldReminderTexts {
    override suspend fun texts(
        minutes: Int,
        tripTitle: String?,
        partySize: Int?,
    ): Pair<String, String> {
        val title = getPluralString(Res.plurals.hold_reminder_title, minutes, minutes)
        val body =
            if (tripTitle != null && partySize != null) {
                getString(Res.string.hold_reminder_body, tripTitle, getPluralString(Res.plurals.booking_party_count, partySize, partySize))
            } else {
                getString(Res.string.hold_reminder_body_plain)
            }
        return title to body
    }
}

/**
 * The reminder's rule (PLAN M3-M7): one reminder per hold, [lead] before the seats are released, by the server's clock.
 *
 * - The delay is what the [HoldDeadline] has left (`holdExpiresAt − serverNow`, anchored to the device's clock when the
 *   answer arrived) minus [lead], so a phone whose clock is minutes off still fires on time; the platform schedules it
 *   relative to now, never at a wall-clock time.
 * - Less than [lead] left: nothing is scheduled (a reminder then would come as the seats go). One already shown stays.
 * - Read again (the hold screen starts, Home's card reads): moved to the fresh deadline, replacing the pending one.
 * - Once its time has passed it has fired: a later read, however close, does not schedule it a second time.
 * - One hold at a time (PLAN M2-M4): a new hold's reminder replaces the previous hold's.
 *
 * Scheduling needs no notification permission on either platform; whether it shows is the OS's call when it fires,
 * so a permission granted after the hold was placed still gets it.
 *
 * App-wide (one Koin single), so the placement, the hold screen and Home's card share what was scheduled. That memory
 * dies with the process; the platforms keep the reminder itself.
 */
internal class HoldReminderPlanner(
    private val reminders: HoldReminders,
    private val texts: HoldReminderTexts = ResourceHoldReminderTexts,
    private val clock: Clock = Clock.System,
    private val lead: Duration = DEFAULT_LEAD,
) : HoldReminderSchedule {
    /** The hold whose reminder is scheduled, and when (device clock) it is due. */
    private var scheduled: Pair<String, Instant>? = null

    override suspend fun onHeld(
        ref: String,
        deadline: HoldDeadline,
        tripTitle: String?,
        partySize: Int?,
    ) {
        val now = clock.now()
        val previous = scheduled
        if (previous != null && previous.first != ref) {
            // Replaced: a new hold took over from that one.
            reminders.cancel(previous.first)
            scheduled = null
        }
        // Already due: it has been shown (or the OS is about to). Never twice for one hold.
        if (previous != null && previous.first == ref && previous.second <= now) return
        val left = deadline.remaining(now)
        val fireIn = left - lead
        if (fireIn <= Duration.ZERO) return
        val (title, body) =
            try {
                texts.texts(lead.inWholeMinutes.toInt(), tripTitle, partySize)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // A reminder is a nicety: a resource that cannot be read must not fail the hold.
                Logger.withTag(LOG_TAG).w(failed) { "No reminder texts" }
                return
            }
        reminders.schedule(HoldReminder(ref = ref, fireIn = fireIn, releasedIn = left, title = title, body = body))
        scheduled = ref to now + fireIn
    }

    override fun onEnded(ref: String) {
        reminders.cancel(ref)
        if (scheduled?.first == ref) scheduled = null
    }

    companion object {
        /** PLAN M3-M7: "fire at `holdExpiresAt − 3 min` (config)". */
        val DEFAULT_LEAD = 3.minutes

        private const val LOG_TAG = "HoldReminder"
    }
}
