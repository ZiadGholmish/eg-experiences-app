package eg.bahr.core.testing

import eg.bahr.core.datastore.MAX_STORED_WAITLIST_JOINS
import eg.bahr.core.datastore.StoredWaitlistJoin
import eg.bahr.core.datastore.WaitlistJoinsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

/*
 * The waiting-list fakes core:catalog's, feature:home's and the trip-area features' tests share (M4-M7, M4-M1c
 * review #7), over core:datastore's store (the approved test-only edge core:testing → core:datastore).
 * The `WaitlistMemory` built over them is core:catalog's, which this module cannot see, so each test
 * module keeps its own one-line `waitlistMemory()` helper.
 */

/**
 * Hand-written fake of core:datastore's store, with the same upsert-by-departure and cap. One
 * instance shared by two view models is "the app restarted over the same file". [failWrites] makes
 * save and remove throw, as a broken preferences file does. [removals] records every remove call,
 * an empty one included (the real store's tidy-up of older builds' phone joins, M4-M13).
 */
class FakeWaitlistJoinsStore(
    initial: List<StoredWaitlistJoin> = emptyList(),
    var failWrites: Boolean = false,
) : WaitlistJoinsStore {
    override val joins = MutableStateFlow(initial)
    val removals = mutableListOf<Set<String>>()

    override suspend fun save(join: StoredWaitlistJoin) {
        if (failWrites) throw StorageFailure("save")
        joins.value = (joins.value.filter { it.departureId != join.departureId } + join).takeLast(MAX_STORED_WAITLIST_JOINS)
    }

    override suspend fun remove(departureIds: Set<String>) {
        if (failWrites) throw StorageFailure("remove")
        removals += departureIds
        joins.value = joins.value.filter { it.departureId !in departureIds }
    }
}

/** A store whose file cannot be read or written at all. */
class BrokenWaitlistJoinsStore : WaitlistJoinsStore {
    override val joins: Flow<List<StoredWaitlistJoin>> = flow { throw StorageFailure("read") }

    override suspend fun save(join: StoredWaitlistJoin): Unit = throw StorageFailure("save")

    override suspend fun remove(departureIds: Set<String>): Unit = throw StorageFailure("remove")
}

/** Stands in for the IOException a real preferences file throws (common code has no IOException). */
class StorageFailure(
    what: String,
) : IllegalStateException("fake store: $what failed")

/** A clock that reads [now] until moved. */
class FixedClock(
    var now: Instant = TEST_NOW,
) : Clock {
    override fun now(): Instant = now
}

/**
 * Morning of Sat 10 Oct 2026 in Cairo: the first Saturday of the trip fixtures (feature:trip-details'
 * `TripFixtures.saturdays`, feature:home's copy), so every fixture date is today or later and a stored
 * join is not pruned as passed. Fixed, so the tests do not start failing once the real calendar
 * passes those dates.
 */
val TEST_NOW: Instant = Instant.parse("2026-10-10T06:00:00Z")

/** A stored join as the trip page writes it; defaults are the fixtures' sold-out Saturday. */
fun storedJoin(
    departureId: String = "dep-3",
    tripSlug: String = "burullus-dawn",
    date: String = "2026-10-24",
    partySize: Int = 2,
    email: String = "sara@example.com",
) = StoredWaitlistJoin(departureId, tripSlug, date, partySize, email, joinedAt = TEST_NOW.toString())
