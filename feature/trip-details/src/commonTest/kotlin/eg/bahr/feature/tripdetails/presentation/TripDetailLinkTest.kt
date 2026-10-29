package eg.bahr.feature.tripdetails.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.tripdetails.data.FakeTripDetailRepository
import eg.bahr.feature.tripdetails.data.TripFixtures.detail
import eg.bahr.feature.tripdetails.data.TripFixtures.saturdays
import eg.bahr.feature.tripdetails.data.waitlistMemory
import eg.bahr.feature.tripdetails.model.DepartureDto
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M3-M5 on the trip page: a link's date (a push's or an email's `?date=`) is picked once from the fresh dates, the
 * "A place opened" band only when the push said so and the date is really bookable; the push pre-prompt is placed
 * after a successful join; a signed-in person's waiting-list form starts with their email.
 */
class TripDetailLinkTest {
    private fun repo(
        dates: List<DepartureDto> = saturdays(),
        live: AppResult<List<DepartureDto>> = AppResult.Success(dates),
        email: String? = null,
    ) = FakeTripDetailRepository(
        tripBySlug = { AppResult.Success(detail(dates = dates)) },
        departuresFor = { live },
        joinWaitlist = { _, _ -> AppResult.Success(Unit) },
        accountEmail = email,
    )

    private fun link(
        date: String,
        placeOpened: Boolean = true,
    ) = TripLink(LocalDate.parse(date), placeOpened)

    @Test
    fun `a place-opened push picks its date and says so when the fresh dates show it bookable`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(), waitlistMemory(), link("2026-10-17"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("dep-2", state.selectedDepartureId)
            assertEquals(WaitlistOutcome("dep-2", LocalDate.parse("2026-10-17"), WaitlistOutcome.Kind.PlaceOpened), state.waitlistOutcome)
            assertEquals(TripCta.Continue, state.cta)
            assertTrue(state.revealDates, "the page scrolls to the dates")

            vm.datesRevealed()
            assertFalse(vm.uiState.value.revealDates, "once")
        }

    @Test
    fun `the place is gone by the tap - the sold-out date is picked and nothing is claimed`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(), waitlistMemory(), link("2026-10-24"))
            advanceUntilIdle()

            assertEquals("dep-3", vm.uiState.value.selectedDepartureId)
            assertNull(vm.uiState.value.waitlistOutcome)
            assertEquals(TripCta.SoldOut, vm.uiState.value.cta)
        }

    @Test
    fun `a link that is not a place-opened push picks the date without the band`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(), waitlistMemory(), link("2026-10-17", placeOpened = false))
            advanceUntilIdle()

            assertEquals("dep-2", vm.uiState.value.selectedDepartureId)
            assertNull(vm.uiState.value.waitlistOutcome)
        }

    @Test
    fun `a date no longer listed opens the page with none picked`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(), waitlistMemory(), link("2026-12-25"))
            advanceUntilIdle()

            assertNull(vm.uiState.value.selectedDepartureId)
            assertNull(vm.uiState.value.waitlistOutcome)
            assertFalse(vm.uiState.value.revealDates)
        }

    @Test
    fun `a failed live read picks from the trip's own dates and claims nothing`() =
        runViewModelTest {
            val vm =
                TripDetailViewModel("burullus-dawn", repo(live = AppResult.Failure(AppError.Network)), waitlistMemory(), link("2026-10-17"))
            advanceUntilIdle()

            assertEquals("dep-2", vm.uiState.value.selectedDepartureId)
            assertNull(vm.uiState.value.waitlistOutcome, "only a fresh read may say the place is open")
        }

    @Test
    fun `the link is applied once - picking another date later is the person's`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(), waitlistMemory(), link("2026-10-17"))
            advanceUntilIdle()

            vm.selectDeparture("dep-4")
            vm.load()
            advanceUntilIdle()

            assertEquals("dep-4", vm.uiState.value.selectedDepartureId)
        }

    @Test
    fun `a successful join places the push pre-prompt until it is done`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(), waitlistMemory())
            advanceUntilIdle()
            vm.selectDeparture(SOLD_OUT)
            vm.openWaitlist()
            vm.setWaitlistEmail("sara@example.com")
            assertFalse(vm.uiState.value.askForPush)

            vm.joinWaitlist()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.askForPush)

            vm.pushPromptDone()
            assertFalse(vm.uiState.value.askForPush)
        }

    @Test
    fun `signed in - the form starts with the account's email - which can still be changed`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(email = "sara@example.com"), waitlistMemory())
            advanceUntilIdle()
            vm.selectDeparture(SOLD_OUT)

            vm.openWaitlist()

            assertEquals(
                "sara@example.com",
                vm.uiState.value.openWaitlist
                    ?.email,
            )
            vm.setWaitlistEmail("other@example.com")
            assertEquals(
                "other@example.com",
                vm.uiState.value.openWaitlist
                    ?.email,
            )
        }

    @Test
    fun `signed out - the form starts empty`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(email = null), waitlistMemory())
            advanceUntilIdle()
            vm.selectDeparture(SOLD_OUT)

            vm.openWaitlist()

            assertEquals(
                "",
                vm.uiState.value.openWaitlist
                    ?.email,
            )
        }

    private companion object {
        /** The sold-out Saturday in the fixtures. */
        const val SOLD_OUT = "dep-3"
    }
}
