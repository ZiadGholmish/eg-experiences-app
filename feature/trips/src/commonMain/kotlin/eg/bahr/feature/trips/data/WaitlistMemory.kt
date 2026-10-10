package eg.bahr.feature.trips.data

import co.touchlab.kermit.Logger
import eg.bahr.core.datastore.StoredWaitlistJoin
import eg.bahr.core.datastore.WaitlistJoinsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * A waiting-list join this device made (M4-M5), read back from `core:datastore`: [date] is the
 * departure's Cairo calendar date, [phone] the number it was left on.
 */
internal data class JoinedWaitlist(
    val departureId: String,
    val tripSlug: String,
    val date: LocalDate,
    val partySize: Int,
    val phone: String,
)

/**
 * The trips feature's view of the stored waiting-list joins: typed, and never failing a screen.
 *
 * The backend has no anonymous "my waiting lists" read (that waits for OTP identity, M6-M1), so the
 * device remembers its own joins. Reading or writing the file is extra: if it fails the page works
 * as before (the join is only remembered for this visit), so failures are logged, never shown.
 *
 * [clock] gives "today" as a Cairo calendar date, the zone departure dates are in (the backend lists
 * departures "from today (Africa/Cairo) on").
 */
internal class WaitlistMemory(
    private val store: WaitlistJoinsStore,
    private val clock: Clock = Clock.System,
) {
    /** Every stored join, oldest first. An entry whose date does not parse is left out (and pruned by [forgetPassed]). */
    val joins: Flow<List<JoinedWaitlist>> =
        store.joins
            .map { stored -> stored.mapNotNull { it.parsed() } }
            .catch {
                // Exceptions only, as in [safely]: an `Error` is a build defect, not a storage failure, and must surface.
                if (it !is Exception) throw it
                log.w(it) { "Waiting-list joins not read" }
                emit(emptyList())
            }

    /** Today in Cairo. A stored date before it has run: nothing left to wait for. */
    fun today(): LocalDate = clock.todayIn(CAIRO)

    suspend fun remember(
        departureId: String,
        tripSlug: String,
        date: LocalDate,
        partySize: Int,
        phone: String,
    ) = safely("remember") {
        store.save(
            StoredWaitlistJoin(
                departureId = departureId,
                tripSlug = tripSlug,
                date = date.toString(),
                partySize = partySize,
                phone = phone,
                joinedAt = clock.now().toString(),
            ),
        )
    }

    suspend fun forget(departureIds: Set<String>) {
        if (departureIds.isNotEmpty()) safely("forget") { store.remove(departureIds) }
    }

    /**
     * Drops every join whose date has passed, on any trip, and any entry that no longer parses, so a
     * phone number does not outlive its trip on the device. Run where the app starts (Home) and on a
     * trip page.
     */
    suspend fun forgetPassed() =
        safely("prune") {
            val today = today()
            val stale = store.joins.first().filter { it.parsed()?.date?.let { date -> date < today } ?: true }
            forget(stale.mapTo(mutableSetOf()) { it.departureId })
        }

    private suspend fun safely(
        what: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The file layer throws IOException on Android and platform exceptions on iOS; neither is
            // the user's problem here, so any of them is logged and the page carries on. An `Error`
            // (e.g. NoClassDefFoundError for java.time below API 26) is deliberately not caught: that
            // is a build defect, prevented by core library desugaring in the Android convention, and
            // hiding it here would only move the crash elsewhere.
            log.w(e) { "Waiting-list joins: $what failed" }
        }
    }

    private fun StoredWaitlistJoin.parsed(): JoinedWaitlist? {
        // Only a malformed value (DateTimeFormatException is an IllegalArgumentException): anything
        // else, such as a missing java.time on an old Android, is a build defect and must surface.
        val day =
            try {
                LocalDate.parse(date)
            } catch (_: IllegalArgumentException) {
                return null
            }
        return JoinedWaitlist(departureId, tripSlug, day, partySize, phone)
    }

    private companion object {
        val CAIRO = TimeZone.of("Africa/Cairo")
        val log = Logger.withTag("WaitlistMemory")
    }
}
