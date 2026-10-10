package eg.bahr.feature.trips.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.BrokenWaitlistJoinsStore
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.FakeWaitlistJoinsStore
import eg.bahr.feature.trips.data.FixedClock
import eg.bahr.feature.trips.data.TEST_NOW
import eg.bahr.feature.trips.data.TripFixtures.detail
import eg.bahr.feature.trips.data.TripFixtures.saturdays
import eg.bahr.feature.trips.data.WaitlistMemory
import eg.bahr.feature.trips.data.storedJoin
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripDetailDto
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * The trip page remembers waiting-list joins on the device (M4-M5). "Restart" is a new view model
 * over the same store. `dep-3` (Sat 24 Oct) is the fixtures' sold-out date, `dep-2` a bookable one;
 * the clock stands on Sat 10 Oct.
 */
class TripDetailWaitlistMemoryTest {
    private val slug = "burullus-dawn"

    private fun repo(
        dates: List<DepartureDto> = saturdays(),
        live: AppResult<List<DepartureDto>> = AppResult.Success(dates),
        trip: AppResult<TripDetailDto> = AppResult.Success(detail(dates = dates)),
    ) = FakeTripRepository(
        tripBySlug = { trip },
        departuresFor = { live },
        joinWaitlist = { _, _ -> AppResult.Success(Unit) },
    )

    private fun TestScope.open(
        store: FakeWaitlistJoinsStore,
        repository: FakeTripRepository = repo(),
        clock: FixedClock = FixedClock(),
    ): TripDetailViewModel = TripDetailViewModel(slug, repository, WaitlistMemory(store, clock)).also { advanceUntilIdle() }

    private fun soldOut(id: String) = saturdays().map { if (it.id == id) it.asSoldOut() else it }

    private fun DepartureDto.asSoldOut() = copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "SOLD_OUT")

    private fun DepartureDto.asBookable() = copy(seatsRemaining = 3, soldOut = false, bookable = true, unavailableReason = null)

    @Test
    fun `a join is remembered - after a restart the date reads joined with its phone and the form is not offered`() =
        runViewModelTest {
            val store = FakeWaitlistJoinsStore()
            val first = open(store)
            first.selectDeparture("dep-3")
            first.openWaitlist()
            first.setWaitlistPhone("+20 100 123 4567")
            first.increaseWaitlistParty()
            first.joinWaitlist()
            advanceUntilIdle()

            val saved = store.joins.value.single()
            assertEquals("dep-3", saved.departureId)
            assertEquals(slug, saved.tripSlug)
            assertEquals("2026-10-24", saved.date)
            assertEquals(2, saved.partySize)
            assertEquals("+201001234567", saved.phone)
            assertEquals(TEST_NOW.toString(), saved.joinedAt)

            val restarted = open(store)
            assertEquals(mapOf("dep-3" to "+201001234567"), restarted.uiState.value.joinedWaitlists)
            restarted.selectDeparture("dep-3")
            assertEquals("+201001234567", restarted.uiState.value.joinedPhone)
            restarted.openWaitlist()
            assertNull(restarted.uiState.value.openWaitlist, "already on the list: no form")
            assertNull(restarted.uiState.value.waitlistOutcome)
        }

    @Test
    fun `a stored date that is bookable again is dropped and says seats opened up once`() =
        runViewModelTest {
            val store = FakeWaitlistJoinsStore(listOf(storedJoin(departureId = "dep-2", date = "2026-10-17")))

            val vm = open(store)

            assertEquals(WaitlistOutcome("dep-2", saturdays()[1].date, WaitlistOutcome.Kind.SeatsOpened), vm.uiState.value.waitlistOutcome)
            assertTrue(
                vm.uiState.value.joinedWaitlists
                    .isEmpty(),
            )
            assertTrue(store.joins.value.isEmpty())

            val again = open(store)
            assertNull(again.uiState.value.waitlistOutcome, "said once: the join is gone")
        }

    @Test
    fun `a stored date still sold out is kept`() =
        runViewModelTest {
            val store = FakeWaitlistJoinsStore(listOf(storedJoin()))

            val vm = open(store)

            assertEquals(listOf(storedJoin()), store.joins.value)
            assertEquals(setOf("dep-3"), vm.uiState.value.joinedWaitlists.keys)
            assertNull(vm.uiState.value.waitlistOutcome)
        }

    @Test
    fun `a date that has passed is dropped - on any trip`() =
        runViewModelTest {
            val store =
                FakeWaitlistJoinsStore(
                    listOf(storedJoin(), storedJoin(departureId = "other-old", tripSlug = "white-desert", date = "2026-10-09")),
                )
            val clock = FixedClock()

            open(store, clock = clock)
            assertEquals(listOf("dep-3"), store.joins.value.map { it.departureId }, "yesterday's join on another trip is gone")

            // Two weeks on, the sold-out Saturday has run. The server would no longer list it either way.
            clock.now = TEST_NOW + 15.days
            val later = open(store, repo(dates = saturdays().filter { it.id != "dep-3" }), clock)
            assertTrue(store.joins.value.isEmpty())
            assertTrue(
                later.uiState.value.joinedWaitlists
                    .isEmpty(),
            )
        }

    @Test
    fun `a stored date now cancelled or closed or no longer listed is dropped silently`() =
        runViewModelTest {
            val store =
                FakeWaitlistJoinsStore(
                    listOf(
                        storedJoin(departureId = "dep-3"),
                        storedJoin(departureId = "dep-4", date = "2026-10-31"),
                        storedJoin(departureId = "dep-gone", date = "2026-11-07"),
                    ),
                )
            val dates =
                saturdays().map {
                    when (it.id) {
                        "dep-3" -> it.copy(unavailableReason = "CANCELLED")
                        "dep-4" -> it.copy(bookable = false, unavailableReason = "CLOSED")
                        else -> it
                    }
                }

            val vm = open(store, repo(dates = dates))

            assertTrue(store.joins.value.isEmpty())
            assertNull(vm.uiState.value.waitlistOutcome)
        }

    @Test
    fun `another trip's join is not judged against this trip's dates`() =
        runViewModelTest {
            val elsewhere = storedJoin(departureId = "dep-77", tripSlug = "white-desert", date = "2026-10-24")
            val store = FakeWaitlistJoinsStore(listOf(elsewhere))

            val vm = open(store)

            assertEquals(listOf(elsewhere), store.joins.value)
            assertTrue(
                vm.uiState.value.joinedWaitlists
                    .isEmpty(),
                "and it does not read as joined here",
            )
        }

    @Test
    fun `when no read answers nothing is dropped`() =
        runViewModelTest {
            val store = FakeWaitlistJoinsStore(listOf(storedJoin(departureId = "dep-2", date = "2026-10-17")))

            val vm = open(store, repo(live = AppResult.Failure(AppError.Network), trip = AppResult.Failure(AppError.Network)))

            assertEquals(1, store.joins.value.size)
            assertNull(vm.uiState.value.waitlistOutcome)
        }

    @Test
    fun `when the live dates fail the trip's own dates decide`() =
        runViewModelTest {
            val store = FakeWaitlistJoinsStore(listOf(storedJoin(departureId = "dep-2", date = "2026-10-17"), storedJoin()))

            val vm = open(store, repo(live = AppResult.Failure(AppError.Network)))

            assertEquals(listOf("dep-3"), store.joins.value.map { it.departureId })
            assertEquals(
                WaitlistOutcome.Kind.SeatsOpened,
                vm.uiState.value.waitlistOutcome
                    ?.kind,
            )
        }

    @Test
    fun `two joins on two dates are both remembered by date`() =
        runViewModelTest {
            val store = FakeWaitlistJoinsStore(listOf(storedJoin(departureId = "dep-4", date = "2026-10-31", phone = "+201112223334")))
            val vm = open(store, repo(dates = soldOut("dep-4")))
            vm.selectDeparture("dep-3")
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")
            vm.joinWaitlist()
            advanceUntilIdle()

            val restarted = open(store, repo(dates = soldOut("dep-4")))

            assertEquals(mapOf("dep-4" to "+201112223334", "dep-3" to "+201001234567"), restarted.uiState.value.joinedWaitlists)
        }

    @Test
    fun `a store that cannot be written - the join still shows in this visit and no error reaches the page`() =
        runViewModelTest {
            val store = FakeWaitlistJoinsStore(failWrites = true)
            val vm = open(store)
            vm.selectDeparture("dep-3")
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")

            vm.joinWaitlist()
            advanceUntilIdle()

            assertEquals("+201001234567", vm.uiState.value.joinedPhone)
            assertNull(vm.uiState.value.error)
            assertNull(vm.uiState.value.waitlist)
            assertTrue(store.joins.value.isEmpty(), "nothing was written, and nothing crashed")
        }

    @Test
    fun `a store that cannot be read - the page loads and joining works as before`() =
        runViewModelTest {
            val repository = repo()
            val vm = TripDetailViewModel(slug, repository, WaitlistMemory(BrokenWaitlistJoinsStore(), FixedClock()))
            advanceUntilIdle()

            assertEquals(saturdays(), vm.uiState.value.departures)
            assertTrue(
                vm.uiState.value.joinedWaitlists
                    .isEmpty(),
            )
            assertNull(vm.uiState.value.error)

            vm.selectDeparture("dep-3")
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")
            vm.joinWaitlist()
            advanceUntilIdle()
            assertEquals("+201001234567", vm.uiState.value.joinedPhone)
        }

    @Test
    fun `a refused join whose re-read shows seats also forgets a join stored meanwhile`() =
        runViewModelTest {
            val store = FakeWaitlistJoinsStore()
            val repository = repo()
            val vm = open(store, repository)
            vm.selectDeparture("dep-3")
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")
            // Another writer (an earlier visit's late save) stores the date after the form opened.
            store.save(storedJoin())
            advanceUntilIdle()
            repository.joinWaitlist =
                { _, _ -> AppResult.Failure(AppError.Api(code = ApiErrorCodes.CONFLICT, message = "not read", httpStatus = 409)) }
            repository.departuresFor =
                {
                    AppResult.Success(
                        saturdays().map { if (it.id == "dep-3") it.asBookable() else it },
                    )
                }

            vm.joinWaitlist()
            advanceUntilIdle()

            assertEquals(
                WaitlistOutcome.Kind.SeatsOpened,
                vm.uiState.value.waitlistOutcome
                    ?.kind,
            )
            assertTrue(store.joins.value.isEmpty())
        }
}
