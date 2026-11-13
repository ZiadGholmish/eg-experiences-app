package eg.bahr.feature.tripdetails.presentation

import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.tripdetails.data.FakeTripDetailRepository
import eg.bahr.feature.tripdetails.data.TripFixtures.detail
import eg.bahr.feature.tripdetails.data.TripFixtures.saturdays
import eg.bahr.feature.tripdetails.data.TripFixtures.trip
import eg.bahr.feature.tripdetails.data.waitlistMemory
import eg.bahr.feature.tripdetails.model.DepartureDto
import eg.bahr.feature.tripdetails.model.TripDetailDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MV-M9: a trip whose every upcoming date is full shows a disabled "Sold out" from the first frame, the button follows
 * the selected date, and it is never enabled before the live dates are known.
 */
class TripDetailSoldOutTest {
    private fun DepartureDto.full() = copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "SOLD_OUT")

    private fun DepartureDto.closed() = copy(bookable = false, unavailableReason = "CLOSED")

    private fun DepartureDto.cancelled() = copy(bookable = false, unavailableReason = "CANCELLED")

    /** Every Saturday full, as the seeded white-desert-overnight. */
    private fun allFull() = saturdays().map { it.full() }

    private fun repo(
        dates: List<DepartureDto>,
        live: List<DepartureDto> = dates,
    ) = FakeTripDetailRepository(
        tripBySlug = { AppResult.Success(detail(dates = dates)) },
        departuresFor = { AppResult.Success(live) },
    )

    private fun open(repository: FakeTripDetailRepository) = TripDetailViewModel(SLUG, repository, waitlistMemory())

    @Test
    fun `every date full reads a disabled Sold out with nothing picked and the waiting list stays offered`() =
        runViewModelTest {
            val vm = open(repo(allFull()))
            advanceUntilIdle()

            assertEquals(TripCta.SoldOut, vm.uiState.value.cta)
            assertFalse(vm.uiState.value.ctaEnabled)

            vm.selectDeparture("dep-2")
            vm.openWaitlist()
            assertEquals(TripCta.SoldOut, vm.uiState.value.cta)
            assertFalse(vm.uiState.value.ctaEnabled)
            assertNotNull(vm.uiState.value.openWaitlist)
        }

    /** The seeded wadi-el-rayan: a date past its cutoff and a full one. Its card says sold out, so the page does too. */
    @Test
    fun `a closed date does not count as bookable`() =
        runViewModelTest {
            val dates = saturdays().take(2).let { (closed, full) -> listOf(closed.closed(), full.full()) }
            val vm = open(repo(dates))
            advanceUntilIdle()

            assertEquals(TripCta.SoldOut, vm.uiState.value.cta)
            assertFalse(vm.uiState.value.ctaEnabled)
        }

    /** Nothing full, nothing open: not "sold out"; the button takes the reader to the band, which says why. */
    @Test
    fun `only cancelled and closed dates are not sold out`() =
        runViewModelTest {
            val dates = saturdays().take(2).let { (a, b) -> listOf(a.cancelled(), b.closed()) }
            val vm = open(repo(dates))
            advanceUntilIdle()

            assertEquals(TripCta.ChooseDate, vm.uiState.value.cta)
            assertTrue(vm.uiState.value.ctaEnabled)
        }

    @Test
    fun `the list card's sold-out flag disables the button on the first frame`() =
        runViewModelTest {
            val repository =
                FakeTripDetailRepository(
                    tripBySlug = { awaitCancellation() },
                    departuresFor = { awaitCancellation() },
                    cards = mapOf(SLUG to trip(1, nextDeparture = NextDepartureDto(seatsRemaining = 0, soldOut = true)).copy(slug = SLUG)),
                )
            val vm = open(repository)

            // No read has run yet: this is what the first frame draws.
            assertEquals(TripCta.SoldOut, vm.uiState.value.cta)
            assertFalse(vm.uiState.value.ctaEnabled)
            advanceUntilIdle()
            assertEquals(TripCta.SoldOut, vm.uiState.value.cta)
        }

    @Test
    fun `while the dates load the button is disabled until the live read answers`() =
        runViewModelTest {
            val trip = CompletableDeferred<AppResult<TripDetailDto>>()
            val live = CompletableDeferred<AppResult<List<DepartureDto>>>()
            val vm = open(FakeTripDetailRepository(tripBySlug = { trip.await() }, departuresFor = { live.await() }))
            advanceUntilIdle()
            assertEquals(TripCta.ChooseDate, vm.uiState.value.cta)
            assertFalse(vm.uiState.value.ctaEnabled, "nothing known")

            trip.complete(AppResult.Success(detail()))
            advanceUntilIdle()
            // The trip's own dates are drawn (seats shimmering) and can be picked, but nothing is enabled yet.
            assertFalse(vm.uiState.value.ctaEnabled, "trip landed, live dates on their way")
            vm.selectDeparture("dep-1")
            assertEquals(TripCta.Continue, vm.uiState.value.cta)
            assertFalse(vm.uiState.value.ctaEnabled, "picked from the trip's copy")

            live.complete(AppResult.Success(saturdays()))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.ctaEnabled)
        }

    @Test
    fun `a failed live read leaves the trip's own dates to decide`() =
        runViewModelTest {
            val vm =
                open(
                    FakeTripDetailRepository(
                        tripBySlug = { AppResult.Success(detail()) },
                        departuresFor = { AppResult.Failure(AppError.Network) },
                    ),
                )
            advanceUntilIdle()

            assertEquals(TripCta.ChooseDate, vm.uiState.value.cta)
            assertTrue(vm.uiState.value.ctaEnabled)
        }

    /** Seat counts are hints: a card that said full is overruled by live dates with seats. */
    @Test
    fun `live dates with seats overrule a stale sold-out card`() =
        runViewModelTest {
            val repository =
                repo(saturdays()).apply {
                    cards = mapOf(SLUG to trip(1, nextDeparture = NextDepartureDto(seatsRemaining = 0, soldOut = true)).copy(slug = SLUG))
                }
            val vm = open(repository)
            assertEquals(TripCta.SoldOut, vm.uiState.value.cta)
            advanceUntilIdle()

            assertEquals(TripCta.ChooseDate, vm.uiState.value.cta)
            assertTrue(vm.uiState.value.ctaEnabled)
        }

    @Test
    fun `a sold-out date picked among open ones disables the button until another is picked`() =
        runViewModelTest {
            val vm = open(repo(saturdays()))
            advanceUntilIdle()

            vm.selectDeparture("dep-3")
            assertEquals(TripCta.SoldOut, vm.uiState.value.cta)
            assertFalse(vm.uiState.value.ctaEnabled)

            vm.selectDeparture("dep-3")
            assertEquals(TripCta.ChooseDate, vm.uiState.value.cta)
            assertTrue(vm.uiState.value.ctaEnabled)
        }

    @Test
    fun `one date left with one seat is bookable`() =
        runViewModelTest {
            val dates = allFull().mapIndexed { i, d -> if (i == 3) saturdays()[3].copy(seatsRemaining = 1) else d }
            val vm = open(repo(dates))
            advanceUntilIdle()

            assertEquals(TripCta.ChooseDate, vm.uiState.value.cta)
            assertTrue(vm.uiState.value.ctaEnabled)

            vm.selectDeparture("dep-4")
            assertEquals(TripCta.Continue, vm.uiState.value.cta)
            assertTrue(vm.uiState.value.ctaEnabled)
            assertEquals("dep-4", vm.uiState.value.continueDepartureId)
        }

    private companion object {
        const val SLUG = "burullus-dawn"
    }
}
