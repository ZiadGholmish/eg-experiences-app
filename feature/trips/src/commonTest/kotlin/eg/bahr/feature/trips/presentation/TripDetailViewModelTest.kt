package eg.bahr.feature.trips.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.detail
import eg.bahr.feature.trips.data.TripFixtures.saturdays
import eg.bahr.feature.trips.data.TripFixtures.trip
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripDetailViewModelTest {
    private fun loaded() =
        FakeTripRepository(
            tripBySlug = { AppResult.Success(detail()) },
            departuresFor = { AppResult.Success(saturdays()) },
        )

    @Test
    fun `nothing is selected at first so the button asks for a date`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", loaded())
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals(4, state.departures.size)
            assertNull(state.selectedDeparture)
            assertEquals(TripCta.ChooseDate, state.cta)
            assertNull(state.continueDepartureId)
        }

    @Test
    fun `selecting a bookable date continues with that date and tapping it again clears it`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", loaded())
            advanceUntilIdle()

            vm.selectDeparture("dep-2")
            assertEquals(TripCta.Continue, vm.uiState.value.cta)
            assertEquals("dep-2", vm.uiState.value.continueDepartureId)

            vm.selectDeparture("dep-4")
            assertEquals("dep-4", vm.uiState.value.continueDepartureId)

            vm.selectDeparture("dep-4")
            assertNull(vm.uiState.value.selectedDepartureId)
            assertEquals(TripCta.ChooseDate, vm.uiState.value.cta)
        }

    @Test
    fun `a sold-out date can be picked but cannot continue`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", loaded())
            advanceUntilIdle()

            vm.selectDeparture("dep-3")

            val state = vm.uiState.value
            assertEquals("dep-3", state.selectedDepartureId)
            assertEquals(TripCta.SoldOut, state.cta)
            assertNull(state.continueDepartureId)
            // The notice points at the next date with seats.
            assertEquals("dep-4", state.alternative?.id)
        }

    @Test
    fun `a sold-out date with no later seats points back to an earlier one`() =
        runViewModelTest {
            val dates = saturdays().take(3)
            val repo =
                FakeTripRepository(tripBySlug = { AppResult.Success(detail(dates = dates)) }, departuresFor = { AppResult.Success(dates) })
            val vm = TripDetailViewModel("burullus-dawn", repo)
            advanceUntilIdle()

            vm.selectDeparture("dep-3")

            assertEquals(
                "dep-1",
                vm.uiState.value.alternative
                    ?.id,
            )
        }

    @Test
    fun `a closed date that is not sold out cannot be picked and neither can an unknown one`() =
        runViewModelTest {
            val dates = saturdays().mapIndexed { i, d -> if (i == 0) d.copy(bookable = false) else d }
            val repo = FakeTripRepository(tripBySlug = { AppResult.Success(detail()) }, departuresFor = { AppResult.Success(dates) })
            val vm = TripDetailViewModel("burullus-dawn", repo)
            advanceUntilIdle()

            vm.selectDeparture("dep-1")
            vm.selectDeparture("nope")

            assertNull(vm.uiState.value.selectedDepartureId)
        }

    @Test
    fun `the list card's text shows while the trip loads`() =
        runViewModelTest {
            val card = trip(1, title = "Dawn on Lake Burullus")
            val repo =
                FakeTripRepository(
                    tripBySlug = { awaitCancellation() },
                    departuresFor = { awaitCancellation() },
                    cards = mapOf("burullus-dawn" to card),
                )
            val vm = TripDetailViewModel("burullus-dawn", repo)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isLoading)
            assertEquals(card, state.preview)
            assertTrue(state.departuresLoading)
        }

    @Test
    fun `failed departures fall back to the trip's own dates`() =
        runViewModelTest {
            val repo =
                FakeTripRepository(tripBySlug = { AppResult.Success(detail()) }, departuresFor = { AppResult.Failure(AppError.Network) })
            val vm = TripDetailViewModel("burullus-dawn", repo)
            advanceUntilIdle()

            assertEquals(4, vm.uiState.value.departures.size)
            assertFalse(vm.uiState.value.departuresLoading)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `a failed trip is screen state and retry loads it`() =
        runViewModelTest {
            var fail = true
            val repo =
                FakeTripRepository(
                    tripBySlug = { if (fail) AppResult.Failure(AppError.Network) else AppResult.Success(detail()) },
                    departuresFor = { AppResult.Success(saturdays()) },
                )
            val vm = TripDetailViewModel("burullus-dawn", repo)
            advanceUntilIdle()
            assertEquals(AppError.Network, vm.uiState.value.error)

            fail = false
            vm.load()
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertEquals(
                "burullus-dawn",
                vm.uiState.value.trip
                    ?.slug,
            )
        }

    @Test
    fun `a refresh that drops the selected date clears the selection`() =
        runViewModelTest {
            var dates = saturdays()
            val repo = FakeTripRepository(tripBySlug = { AppResult.Success(detail()) }, departuresFor = { AppResult.Success(dates) })
            val vm = TripDetailViewModel("burullus-dawn", repo)
            advanceUntilIdle()
            vm.selectDeparture("dep-1")

            dates = saturdays().drop(1)
            vm.load()
            advanceUntilIdle()

            assertNull(vm.uiState.value.selectedDepartureId)
        }
}
