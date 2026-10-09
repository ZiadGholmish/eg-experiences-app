package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.booking.data.BookingFixtures.SLUG
import eg.bahr.feature.booking.data.BookingFixtures.held
import eg.bahr.feature.booking.data.BookingFixtures.saturdays
import eg.bahr.feature.booking.data.BookingFixtures.trip
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.model.HeldSeatsDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingViewModelTest {
    private fun loaded(dates: () -> List<eg.bahr.feature.booking.model.BookingDepartureDto> = { saturdays() }) =
        FakeBookingRepository(
            tripBySlug = { AppResult.Success(trip()) },
            departuresFor = { AppResult.Success(dates()) },
            placeHold = { AppResult.Success(held()) },
        )

    private fun BookingViewModel.fillGuest() {
        setGuestName("ندى حسن")
        setGuestPhone("010 1234 5678")
    }

    @Test
    fun `the date picked on the trip page starts selected`() =
        runViewModelTest {
            val vm = BookingViewModel(SLUG, "dep-2", loaded())
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals("dep-2", state.selectedDeparture?.id)
            assertEquals(4, state.departures.size)
        }

    @Test
    fun `a picked date that is no longer bookable is dropped`() =
        runViewModelTest {
            // dep-3 is sold out by the time the screen reads the dates.
            val vm = BookingViewModel(SLUG, "dep-3", loaded())
            advanceUntilIdle()

            assertNull(vm.uiState.value.selectedDepartureId)
            assertFalse(vm.uiState.value.canPlaceHold)
        }

    @Test
    fun `only an open date can be picked`() =
        runViewModelTest {
            val dates = saturdays().mapIndexed { i, d -> if (i == 0) d.copy(bookable = false, unavailableReason = "CANCELLED") else d }
            val vm = BookingViewModel(SLUG, "dep-2", loaded { dates })
            advanceUntilIdle()

            vm.selectDeparture("dep-1") // cancelled
            vm.selectDeparture("dep-3") // sold out
            vm.selectDeparture("nope")
            assertEquals("dep-2", vm.uiState.value.selectedDepartureId)

            vm.selectDeparture("dep-4")
            assertEquals("dep-4", vm.uiState.value.selectedDepartureId)
        }

    @Test
    fun `an unknown reason is not bookable even if bookable says so`() =
        runViewModelTest {
            val dates = saturdays().mapIndexed { i, d -> if (i == 1) d.copy(unavailableReason = "WEATHER") else d }
            val vm = BookingViewModel(SLUG, "dep-2", loaded { dates })
            advanceUntilIdle()

            assertNull(vm.uiState.value.selectedDepartureId)
        }

    @Test
    fun `the party stays between one and the policy's maximum`() =
        runViewModelTest {
            val repo = loaded().apply { tripBySlug = { AppResult.Success(trip(maxPartySize = 3)) } }
            val vm = BookingViewModel(SLUG, "dep-1", repo)
            advanceUntilIdle()

            vm.decreaseParty()
            assertEquals(1, vm.uiState.value.partySize)
            assertFalse(vm.uiState.value.canDecreaseParty)

            repeat(5) { vm.increaseParty() }
            assertEquals(3, vm.uiState.value.partySize)
            assertEquals(3, vm.uiState.value.maxPartySize)
            assertFalse(vm.uiState.value.canIncreaseParty)
        }

    @Test
    fun `without a stated policy the party limit falls back to six`() =
        runViewModelTest {
            val repo = loaded().apply { tripBySlug = { AppResult.Success(trip(maxPartySize = null)) } }
            val vm = BookingViewModel(SLUG, "dep-1", repo)
            advanceUntilIdle()

            assertEquals(BookingUiState.FALLBACK_MAX_PARTY_SIZE, vm.uiState.value.maxPartySize)
        }

    @Test
    fun `the phone keeps digits and a leading plus and must match the contract's pattern`() =
        runViewModelTest {
            val vm = BookingViewModel(SLUG, "dep-1", loaded())
            advanceUntilIdle()

            vm.setGuestPhone("+20 10-1234 5678")
            assertEquals("+201012345678", vm.uiState.value.guestPhone)
            assertTrue(vm.uiState.value.isPhoneValid)

            vm.setGuestPhone("0101")
            assertFalse(vm.uiState.value.isPhoneValid)
        }

    @Test
    fun `a hold needs an open date and a name and a valid phone`() =
        runViewModelTest {
            val vm = BookingViewModel(SLUG, "dep-1", loaded())
            advanceUntilIdle()
            assertFalse(vm.uiState.value.canPlaceHold)

            vm.setGuestName("   ")
            vm.setGuestPhone("01012345678")
            assertFalse(vm.uiState.value.canPlaceHold)

            vm.setGuestName("ندى")
            assertTrue(vm.uiState.value.canPlaceHold)
        }

    @Test
    fun `placing a hold sends the date and party and guest and hands over the server's hold once`() =
        runViewModelTest {
            val repo = loaded()
            val vm = BookingViewModel(SLUG, "dep-1", repo)
            advanceUntilIdle()
            vm.increaseParty()
            vm.fillGuest()

            vm.placeHold()
            advanceUntilIdle()

            val request = repo.holdRequests.single()
            assertEquals("dep-1", request.departureId)
            assertEquals(2, request.partySize)
            assertEquals("ندى حسن", request.guest.name)
            assertEquals("01012345678", request.guest.phone)
            // Left to the server: it defaults to the request's Accept-Language.
            assertNull(request.guest.locale)
            assertEquals(held(), vm.uiState.value.held)

            vm.onHeldHandled()
            assertNull(vm.uiState.value.held)
        }

    @Test
    fun `while a hold is in flight a second tap places nothing`() =
        runViewModelTest {
            val answer = CompletableDeferred<AppResult<HeldSeatsDto>>()
            val repo = loaded().apply { placeHold = { answer.await() } }
            val vm = BookingViewModel(SLUG, "dep-1", repo)
            advanceUntilIdle()
            vm.fillGuest()

            vm.placeHold()
            runCurrent()
            assertTrue(vm.uiState.value.isPlacingHold)
            assertFalse(vm.uiState.value.canPlaceHold)
            vm.placeHold()
            runCurrent()

            answer.complete(AppResult.Success(held()))
            advanceUntilIdle()
            assertEquals(1, repo.holdRequests.size)
            assertFalse(vm.uiState.value.isPlacingHold)
        }

    @Test
    fun `no seats re-reads the dates and drops a date that has filled up`() =
        runViewModelTest {
            var dates = saturdays()
            val repo =
                loaded { dates }.apply {
                    placeHold = { AppResult.Failure(AppError.Api(ApiErrorCodes.NO_SEATS_AVAILABLE, "gone", 409)) }
                }
            val vm = BookingViewModel(SLUG, "dep-2", repo)
            advanceUntilIdle()
            vm.fillGuest()
            val readsBefore = repo.departureReads

            // Someone else took the last two seats of dep-2 meanwhile.
            dates =
                saturdays().map {
                    if (it.id ==
                        "dep-2"
                    ) {
                        it.copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "SOLD_OUT")
                    } else {
                        it
                    }
                }
            vm.placeHold()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(readsBefore + 1, repo.departureReads)
            assertEquals(ApiErrorCodes.NO_SEATS_AVAILABLE, (state.holdError as AppError.Api).code)
            assertNull(state.selectedDepartureId)
            assertNull(state.held)
            assertFalse(state.isPlacingHold)
        }

    @Test
    fun `a date that closed meanwhile also re-reads the dates`() =
        runViewModelTest {
            val repo =
                loaded().apply {
                    placeHold = { AppResult.Failure(AppError.Api(ApiErrorCodes.DEPARTURE_NOT_OPEN, null, 409)) }
                }
            val vm = BookingViewModel(SLUG, "dep-1", repo)
            advanceUntilIdle()
            vm.fillGuest()
            val readsBefore = repo.departureReads

            vm.placeHold()
            advanceUntilIdle()

            assertEquals(readsBefore + 1, repo.departureReads)
        }

    @Test
    fun `another failure keeps the dates and shows the error until the next edit`() =
        runViewModelTest {
            val repo = loaded().apply { placeHold = { AppResult.Failure(AppError.Network) } }
            val vm = BookingViewModel(SLUG, "dep-1", repo)
            advanceUntilIdle()
            vm.fillGuest()
            val readsBefore = repo.departureReads

            vm.placeHold()
            advanceUntilIdle()
            assertEquals(AppError.Network, vm.uiState.value.holdError)
            assertEquals(readsBefore, repo.departureReads)
            assertEquals("dep-1", vm.uiState.value.selectedDepartureId)

            vm.increaseParty()
            assertNull(vm.uiState.value.holdError)
        }

    @Test
    fun `failed live dates fall back to the trip's own dates`() =
        runViewModelTest {
            val repo = loaded().apply { departuresFor = { AppResult.Failure(AppError.Network) } }
            val vm = BookingViewModel(SLUG, "dep-1", repo)
            advanceUntilIdle()

            assertEquals(4, vm.uiState.value.departures.size)
            assertEquals("dep-1", vm.uiState.value.selectedDepartureId)
        }

    @Test
    fun `a failed trip is screen state and retry loads it`() =
        runViewModelTest {
            var fail = true
            val repo =
                loaded().apply {
                    tripBySlug = { if (fail) AppResult.Failure(AppError.Network) else AppResult.Success(trip()) }
                    departuresFor = { awaitCancellation() }
                }
            val vm = BookingViewModel(SLUG, "dep-1", repo)
            advanceUntilIdle()
            assertEquals(AppError.Network, vm.uiState.value.loadError)

            fail = false
            vm.load()
            advanceUntilIdle()

            assertNull(vm.uiState.value.loadError)
            assertEquals(
                SLUG,
                vm.uiState.value.trip
                    ?.slug,
            )
        }
}
