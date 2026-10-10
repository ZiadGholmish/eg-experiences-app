package eg.bahr.feature.trips.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.trips.data.FakeTripRepository
import eg.bahr.feature.trips.data.TripFixtures.detail
import eg.bahr.feature.trips.data.TripFixtures.saturdays
import eg.bahr.feature.trips.data.waitlistMemory
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.PolicyDto
import eg.bahr.feature.trips.model.WaitlistRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The waiting list on the trip page (M2-M3): offered only under a SOLD_OUT date (D3), sent as the
 * contract's `WaitlistRequest`, and every refusal branched on its code. `dep-3` is the sold-out
 * Saturday in the fixtures.
 */
class TripDetailWaitlistTest {
    private fun repo(
        maxPartySize: Int? = 6,
        join: suspend (String, WaitlistRequest) -> AppResult<Unit> = { _, _ -> AppResult.Success(Unit) },
    ) = FakeTripRepository(
        tripBySlug = { AppResult.Success(detail().copy(policy = PolicyDto(maxPartySize = maxPartySize))) },
        departuresFor = { AppResult.Success(saturdays()) },
        joinWaitlist = join,
    )

    private fun TestScope.soldOutPicked(repository: FakeTripRepository): TripDetailViewModel {
        val vm = TripDetailViewModel("burullus-dawn", repository, waitlistMemory())
        advanceUntilIdle()
        vm.selectDeparture(SOLD_OUT)
        return vm
    }

    private fun refused(code: String) = AppResult.Failure(AppError.Api(code = code, message = "not read", httpStatus = 409))

    @Test
    fun `the form opens only under a sold-out date`() =
        runViewModelTest {
            val vm = TripDetailViewModel("burullus-dawn", repo(), waitlistMemory())
            advanceUntilIdle()

            vm.openWaitlist()
            assertNull(vm.uiState.value.openWaitlist, "nothing selected")

            vm.selectDeparture("dep-2")
            vm.openWaitlist()
            assertNull(vm.uiState.value.openWaitlist, "an open date is booked, not waited for")

            vm.selectDeparture(SOLD_OUT)
            vm.openWaitlist()
            assertEquals(
                SOLD_OUT,
                vm.uiState.value.openWaitlist
                    ?.departureId,
            )
        }

    @Test
    fun `the phone keeps digits and a leading plus and must match the contract pattern`() =
        runViewModelTest {
            val vm = soldOutPicked(repo())
            vm.openWaitlist()

            vm.setWaitlistPhone("+20 100-123")
            assertEquals(
                "+20100123",
                vm.uiState.value.openWaitlist
                    ?.phone,
            )
            assertTrue(
                vm.uiState.value.openWaitlist!!
                    .isPhoneValid,
            )

            vm.setWaitlistPhone("12345")
            assertFalse(
                vm.uiState.value.openWaitlist!!
                    .isPhoneValid,
            )

            vm.setWaitlistPhone("+1234567890123456789")
            assertEquals(
                TripDetailUiState.PHONE_MAX_LENGTH,
                vm.uiState.value.openWaitlist
                    ?.phone
                    ?.length,
            )
        }

    @Test
    fun `an invalid phone is not sent`() =
        runViewModelTest {
            val repository = repo()
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("123")

            vm.joinWaitlist()
            advanceUntilIdle()

            assertTrue(repository.waitlistJoins.isEmpty())
        }

    @Test
    fun `the party stays between one and the policy's maximum and falls back to six`() =
        runViewModelTest {
            val vm = soldOutPicked(repo(maxPartySize = 3))
            vm.openWaitlist()

            vm.decreaseWaitlistParty()
            assertEquals(
                1,
                vm.uiState.value.openWaitlist
                    ?.partySize,
            )
            repeat(5) { vm.increaseWaitlistParty() }
            assertEquals(
                3,
                vm.uiState.value.openWaitlist
                    ?.partySize,
            )

            val fallback = soldOutPicked(repo(maxPartySize = null))
            fallback.openWaitlist()
            repeat(10) { fallback.increaseWaitlistParty() }
            assertEquals(
                TripDetailUiState.FALLBACK_MAX_PARTY_SIZE,
                fallback.uiState.value.openWaitlist
                    ?.partySize,
            )
        }

    @Test
    fun `a join sends the phone and party then confirms with that phone`() =
        runViewModelTest {
            val repository = repo()
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")
            vm.increaseWaitlistParty()

            vm.joinWaitlist()
            advanceUntilIdle()

            assertEquals(listOf(SOLD_OUT to WaitlistRequest(phone = "+201001234567", partySize = 2)), repository.waitlistJoins)
            val state = vm.uiState.value
            assertEquals("+201001234567", state.joinedPhone)
            assertNull(state.openWaitlist)
            // The date is still sold out: nothing about it changed.
            assertEquals(TripCta.SoldOut, state.cta)
        }

    @Test
    fun `a second tap while sending does not send twice`() =
        runViewModelTest {
            val answer = CompletableDeferred<AppResult<Unit>>()
            val repository = repo(join = { _, _ -> answer.await() })
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")

            vm.joinWaitlist()
            advanceUntilIdle()
            assertTrue(
                vm.uiState.value.openWaitlist!!
                    .submitting,
            )
            vm.joinWaitlist()
            answer.complete(AppResult.Success(Unit))
            advanceUntilIdle()

            assertEquals(1, repository.waitlistJoins.size)
        }

    @Test
    fun `a joined date reads as joined and another sold-out date does not`() =
        runViewModelTest {
            val dates = saturdays().map { if (it.id == "dep-1") it.asSoldOut() else it }
            val repository =
                FakeTripRepository(
                    tripBySlug = { AppResult.Success(detail(dates = dates)) },
                    departuresFor = { AppResult.Success(dates) },
                    joinWaitlist = { _, _ -> AppResult.Success(Unit) },
                )
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")
            vm.joinWaitlist()
            advanceUntilIdle()

            vm.selectDeparture("dep-1")
            assertNull(vm.uiState.value.joinedPhone)
            vm.openWaitlist()
            assertEquals(
                "",
                vm.uiState.value.openWaitlist
                    ?.phone,
                "the typed form does not carry over",
            )

            vm.selectDeparture(SOLD_OUT)
            assertEquals("+201001234567", vm.uiState.value.joinedPhone)
        }

    @Test
    fun `CONFLICT means the date has seats again - the dates are read again and it can be booked`() =
        runViewModelTest {
            var reopened = false
            val repository =
                FakeTripRepository(
                    tripBySlug = { AppResult.Success(detail()) },
                    departuresFor = { AppResult.Success(if (reopened) saturdays().map { it.reopened() } else saturdays()) },
                    joinWaitlist = { _, _ ->
                        reopened = true
                        refused(ApiErrorCodes.CONFLICT)
                    },
                )
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")
            val readsBefore = repository.departureReads.size

            vm.joinWaitlist()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(readsBefore + 1, repository.departureReads.size)
            assertEquals(SOLD_OUT, state.selectedDepartureId, "the selection is kept")
            assertEquals(TripCta.Continue, state.cta)
            assertEquals(WaitlistOutcome.Kind.SeatsOpened, state.waitlistOutcome?.kind)
            assertNull(state.openWaitlist)
            assertNull(state.joinedPhone)
        }

    @Test
    fun `DEPARTURE_NOT_OPEN means the date closed - the dates are read again and the selection dropped`() =
        runViewModelTest {
            var cancelled = false
            val repository =
                FakeTripRepository(
                    tripBySlug = { AppResult.Success(detail()) },
                    departuresFor = { AppResult.Success(if (cancelled) saturdays().map { it.cancelled() } else saturdays()) },
                    joinWaitlist = { _, _ ->
                        cancelled = true
                        refused(ApiErrorCodes.DEPARTURE_NOT_OPEN)
                    },
                )
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")

            vm.joinWaitlist()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNull(state.selectedDepartureId)
            assertEquals(WaitlistOutcome.Kind.DateClosed, state.waitlistOutcome?.kind)
            assertEquals(SOLD_OUT, state.waitlistOutcome?.departureId)

            // Picking another date clears the outcome: it was about the old one.
            vm.selectDeparture("dep-2")
            assertNull(vm.uiState.value.waitlistOutcome)
        }

    @Test
    fun `a failed re-read keeps the dates and claims nothing - the form shows the refusal`() =
        runViewModelTest {
            var fail = false
            val repository =
                FakeTripRepository(
                    tripBySlug = { AppResult.Success(detail(dates = emptyList())) },
                    departuresFor = { if (fail) AppResult.Failure(AppError.Network) else AppResult.Success(saturdays()) },
                    joinWaitlist = { _, _ ->
                        fail = true
                        refused(ApiErrorCodes.CONFLICT)
                    },
                )
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")

            vm.joinWaitlist()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(4, state.departures.size)
            assertEquals(SOLD_OUT, state.selectedDepartureId)
            assertNull(state.waitlistOutcome, "no 'seats opened up' without a read that shows them")
            val form = assertNotNull(state.openWaitlist)
            assertEquals(ApiErrorCodes.CONFLICT, (form.error as? AppError.Api)?.code)
            assertFalse(form.submitting)
        }

    @Test
    fun `CONFLICT with a re-read that still shows the date full claims nothing`() =
        runViewModelTest {
            val repository =
                FakeTripRepository(
                    tripBySlug = { AppResult.Success(detail()) },
                    departuresFor = { AppResult.Success(saturdays()) },
                    joinWaitlist = { _, _ -> refused(ApiErrorCodes.CONFLICT) },
                )
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")
            val readsBefore = repository.departureReads.size

            vm.joinWaitlist()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(readsBefore + 1, repository.departureReads.size)
            assertNull(state.waitlistOutcome)
            assertEquals(TripCta.SoldOut, state.cta)
            assertNotNull(state.openWaitlist?.error)
        }

    @Test
    fun `NOT_FOUND with the date gone from the re-read reads as closed`() =
        runViewModelTest {
            var gone = false
            val repository =
                FakeTripRepository(
                    tripBySlug = { AppResult.Success(detail()) },
                    departuresFor = { AppResult.Success(if (gone) saturdays().filterNot { it.id == SOLD_OUT } else saturdays()) },
                    joinWaitlist = { _, _ ->
                        gone = true
                        refused(ApiErrorCodes.NOT_FOUND)
                    },
                )
            val vm = soldOutPicked(repository)
            vm.openWaitlist()
            vm.setWaitlistPhone("+201001234567")

            vm.joinWaitlist()
            advanceUntilIdle()

            assertNull(vm.uiState.value.selectedDepartureId)
            assertEquals(
                WaitlistOutcome.Kind.DateClosed,
                vm.uiState.value.waitlistOutcome
                    ?.kind,
            )
        }

    @Test
    fun `a full list or bad details or no network stay on the form with the error`() =
        runViewModelTest {
            listOf(refused(ApiErrorCodes.RATE_LIMITED), refused(ApiErrorCodes.VALIDATION_FAILED), AppResult.Failure(AppError.Network))
                .forEach { refusal ->
                    val vm = soldOutPicked(repo(join = { _, _ -> refusal }))
                    vm.openWaitlist()
                    vm.setWaitlistPhone("+201001234567")

                    vm.joinWaitlist()
                    advanceUntilIdle()

                    val form = assertNotNull(vm.uiState.value.openWaitlist)
                    assertEquals(refusal.error, form.error)
                    assertFalse(form.submitting)
                    assertNull(vm.uiState.value.joinedPhone)
                    assertNull(vm.uiState.value.waitlistOutcome)

                    // Editing clears the error, so the next try starts clean.
                    vm.setWaitlistPhone("+201001234568")
                    assertNull(
                        vm.uiState.value.openWaitlist
                            ?.error,
                    )
                }
        }

    private fun DepartureDto.asSoldOut() = copy(seatsRemaining = 0, soldOut = true, bookable = false, unavailableReason = "SOLD_OUT")

    private fun DepartureDto.reopened() =
        if (id == SOLD_OUT) copy(seatsRemaining = 2, soldOut = false, bookable = true, unavailableReason = null) else this

    private fun DepartureDto.cancelled() = if (id == SOLD_OUT) copy(unavailableReason = "CANCELLED") else this

    private companion object {
        const val SOLD_OUT = "dep-3"
    }
}
