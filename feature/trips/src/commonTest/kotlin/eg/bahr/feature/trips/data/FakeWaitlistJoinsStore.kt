package eg.bahr.feature.trips.data

import eg.bahr.core.datastore.MAX_STORED_WAITLIST_JOINS
import eg.bahr.core.datastore.StoredWaitlistJoin
import eg.bahr.core.datastore.WaitlistJoinsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Hand-written fake of core:datastore's store, with the same upsert-by-departure and cap. One
 * instance shared by two view models is "the app restarted over the same file". [failWrites] makes
 * save and remove throw, as a broken preferences file does.
 */
internal class FakeWaitlistJoinsStore(
    initial: List<StoredWaitlistJoin> = emptyList(),
    var failWrites: Boolean = false,
) : WaitlistJoinsStore {
    override val joins = MutableStateFlow(initial)

    override suspend fun save(join: StoredWaitlistJoin) {
        if (failWrites) throw StorageFailure("save")
        joins.value = (joins.value.filter { it.departureId != join.departureId } + join).takeLast(MAX_STORED_WAITLIST_JOINS)
    }

    override suspend fun remove(departureIds: Set<String>) {
        if (failWrites) throw StorageFailure("remove")
        joins.value = joins.value.filter { it.departureId !in departureIds }
    }
}

/** A store whose file cannot be read or written at all. */
internal class BrokenWaitlistJoinsStore : WaitlistJoinsStore {
    override val joins: Flow<List<StoredWaitlistJoin>> = flow { throw StorageFailure("read") }

    override suspend fun save(join: StoredWaitlistJoin): Unit = throw StorageFailure("save")

    override suspend fun remove(departureIds: Set<String>): Unit = throw StorageFailure("remove")
}

/** Stands in for the IOException a real preferences file throws (common code has no IOException). */
internal class StorageFailure(
    what: String,
) : IllegalStateException("fake store: $what failed")

/** A clock that reads [now] until moved. */
internal class FixedClock(
    var now: Instant = TEST_NOW,
) : Clock {
    override fun now(): Instant = now
}

/**
 * Morning of Sat 10 Oct 2026 in Cairo: the fixtures' first Saturday (`TripFixtures.saturdays`), so
 * every fixture date is today or later and a stored join is not pruned as passed. Fixed, so the tests
 * do not start failing once the real calendar passes those dates.
 */
internal val TEST_NOW: Instant = Instant.parse("2026-10-10T06:00:00Z")

/** The view models' waiting-list memory over [store] (empty by default) at [clock] (fixed by default). */
internal fun waitlistMemory(
    store: FakeWaitlistJoinsStore = FakeWaitlistJoinsStore(),
    clock: Clock = FixedClock(),
) = WaitlistMemory(store, clock)

/** A stored join as the trip page writes it; defaults are the fixtures' sold-out Saturday. */
internal fun storedJoin(
    departureId: String = "dep-3",
    tripSlug: String = "burullus-dawn",
    date: String = "2026-10-24",
    partySize: Int = 2,
    phone: String = "+201001234567",
) = StoredWaitlistJoin(departureId, tripSlug, date, partySize, phone, joinedAt = TEST_NOW.toString())
