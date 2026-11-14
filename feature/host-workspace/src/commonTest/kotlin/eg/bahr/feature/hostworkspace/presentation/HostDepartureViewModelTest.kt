package eg.bahr.feature.hostworkspace.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.ApiErrorReasons
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.hostworkspace.data.FakeHostWorkspaceRepository
import eg.bahr.feature.hostworkspace.data.apiError
import eg.bahr.feature.hostworkspace.data.sampleDeparture
import eg.bahr.feature.hostworkspace.data.sampleTravellers
import eg.bahr.feature.hostworkspace.model.HostBroadcastDto
import eg.bahr.feature.hostworkspace.model.HostDepartureStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * M5-M1: one date's manifest, its broadcast and its manual bookings, with the rules of M5-B2/B3 and the web's review
 * (M5-W1): 403 and 404 read alike, one Idempotency-Key per form opening reused on every Add of it, the list read again
 * and "check the list" after an unclear outcome, Add gone after IDEMPOTENCY_KEY_REUSED, the broadcast confirmed first
 * and held back until `nextAllowedAt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HostDepartureViewModelTest {
    private val id = "3c1e5a7b-9d2f-4b6a-8c0e-2f4a6b8c0d1e"

    /** The device clock on the test's virtual time, so `delay` and `now` move together. */
    private fun TestScope.clock(start: Instant = START): Clock =
        object : Clock {
            override fun now(): Instant = start + testScheduler.currentTime.milliseconds
        }

    private fun TestScope.viewModel(
        repository: FakeHostWorkspaceRepository,
        clock: Clock = clock(),
    ): HostDepartureViewModel {
        var keys = 0
        return HostDepartureViewModel(id, repository, clock, newKey = { "key-${++keys}" }).also { runCurrent() }
    }

    @Test
    fun `the manifest loads`() =
        runViewModelTest {
            val vm = viewModel(FakeHostWorkspaceRepository())
            val loaded = assertIs<ManifestLoad.Loaded>(vm.uiState.value.load)
            assertEquals(4, loaded.departure.bookings.size)
            assertTrue(vm.uiState.value.open)
        }

    @Test
    fun `a forbidden and an unknown date read alike - not available`() =
        runViewModelTest {
            listOf(apiError(ApiErrorCodes.FORBIDDEN, 403), apiError(ApiErrorCodes.NOT_FOUND, 404)).forEach { refusal ->
                val vm = viewModel(FakeHostWorkspaceRepository(departure = { refusal }))
                assertEquals(ManifestLoad.NotAvailable, vm.uiState.value.load)
            }
        }

    @Test
    fun `a suspended host is told so`() =
        runViewModelTest {
            val vm =
                viewModel(
                    FakeHostWorkspaceRepository(departure = { apiError(ApiErrorCodes.FORBIDDEN, 403, ApiErrorReasons.HOST_SUSPENDED) }),
                )
            assertEquals(ManifestLoad.Suspended, vm.uiState.value.load)
        }

    @Test
    fun `a failed re-read keeps the list on screen`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository()
            val vm = viewModel(repository)
            repository.departure = { AppResult.Failure(AppError.Network) }
            vm.reload()
            runCurrent()
            assertIs<ManifestLoad.Loaded>(vm.uiState.value.load)
            assertFalse(vm.uiState.value.refreshing)
        }

    @Test
    fun `host mode signed out while the date is open - the page goes back`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository()
            val vm = viewModel(repository)
            assertFalse(vm.uiState.value.signedOut)
            repository.sessionFlow.value = SessionState.SignedOut
            runCurrent()
            assertTrue(vm.uiState.value.signedOut)
        }

    // ---------- manual booking ----------

    private fun HostDepartureViewModel.fill(
        name: String = "كريم السيد",
        phone: String = "٠١٠ ٤٤١٢ ٧٧٣٩",
    ) {
        openManualBooking("ar")
        onManualName(name)
        onManualPhone(phone)
        onManualParty(3)
        onManualPaid(true)
        reviewManual()
    }

    @Test
    fun `a manual booking sends Western digits and the guests language - then shows the new list`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository()
            val vm = viewModel(repository)
            vm.fill()
            assertEquals(
                ManualStep.Review,
                vm.uiState.value.manual
                    ?.step,
            )
            vm.addManualBooking()
            runCurrent()

            val (key, booking) = repository.manualBookings.single()
            assertEquals("key-1", key)
            assertEquals("01044127739", booking.phone)
            assertEquals("ar", booking.locale)
            assertEquals(3, booking.partySize)
            assertTrue(booking.paid)
            assertNull(vm.uiState.value.manual)
            assertEquals(ManifestNotice.BookingAdded("كريم السيد"), vm.uiState.value.notice)
        }

    @Test
    fun `fields that do not fit stop at the form - nothing is sent`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository()
            val vm = viewModel(repository)
            vm.fill(name = " ", phone = "12")
            val form = vm.uiState.value.manual!!
            assertEquals(ManualStep.Edit, form.step)
            assertTrue(form.checked && form.nameMissing && form.phoneInvalid)
            vm.addManualBooking()
            runCurrent()
            assertTrue(repository.manualBookings.isEmpty())
        }

    @Test
    fun `no answer - the same key goes again on the hosts next Add - the list is read again - closing asks to check it`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository(createManualBooking = { _, _, _ -> AppResult.Failure(AppError.Timeout) })
            val vm = viewModel(repository)
            vm.fill()
            vm.addManualBooking()
            runCurrent()
            assertEquals(
                ManualProblem.RetrySafe,
                vm.uiState.value.manual
                    ?.problem,
            )
            assertEquals(2, repository.departureReads.size, "read again after the unclear outcome")

            // Never retried by itself: only when the host presses Add again, and then with the same key.
            assertEquals(1, repository.manualBookings.size)
            repository.createManualBooking = { _, _, _ -> AppResult.Failure(AppError.Api(ApiErrorCodes.INTERNAL_ERROR, "x", 503)) }
            vm.addManualBooking()
            runCurrent()
            assertEquals(listOf("key-1", "key-1"), repository.manualBookings.map { it.first })

            vm.closeManual()
            runCurrent()
            assertNull(vm.uiState.value.manual)
            assertEquals(ManifestNotice.CheckList, vm.uiState.value.notice)
        }

    @Test
    fun `an edit after an unclear outcome keeps the key - the server then says reused - Add is gone`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository(createManualBooking = { _, _, _ -> AppResult.Failure(AppError.Network) })
            val vm = viewModel(repository)
            vm.fill()
            vm.addManualBooking()
            runCurrent()
            vm.editManual()
            vm.onManualName("كريم سيد")
            vm.reviewManual()
            repository.createManualBooking = { _, _, _ ->
                apiError(ApiErrorCodes.CONFLICT, 409, ApiErrorReasons.IDEMPOTENCY_KEY_REUSED)
            }
            vm.addManualBooking()
            runCurrent()

            assertEquals(listOf("key-1", "key-1"), repository.manualBookings.map { it.first })
            val form = vm.uiState.value.manual!!
            assertEquals(ManualProblem.KeyReused, form.problem)
            assertFalse(form.canAdd)
            // Add does nothing now, even after another edit: this opening's key may not add anything.
            vm.onManualName("كريم")
            assertEquals(
                ManualProblem.KeyReused,
                vm.uiState.value.manual
                    ?.problem,
            )
            vm.addManualBooking()
            runCurrent()
            assertEquals(2, repository.manualBookings.size)

            vm.closeManual()
            runCurrent()
            assertEquals(ManifestNotice.CheckList, vm.uiState.value.notice)
        }

    @Test
    fun `each opening of the form has its own key`() =
        runViewModelTest {
            val repository =
                FakeHostWorkspaceRepository(createManualBooking = {
                    _,
                    _,
                    _,
                    ->
                    apiError(ApiErrorCodes.NO_SEATS_AVAILABLE, 409)
                })
            val vm = viewModel(repository)
            vm.fill()
            vm.addManualBooking()
            runCurrent()
            assertEquals(
                ManualProblem.NoSeats,
                vm.uiState.value.manual
                    ?.problem,
            )
            vm.closeManual()
            // A clear refusal recorded nothing: no "check the list".
            assertNull(vm.uiState.value.notice)
            vm.fill()
            vm.addManualBooking()
            runCurrent()
            assertEquals(listOf("key-1", "key-2"), repository.manualBookings.map { it.first })
        }

    @Test
    fun `a refusal clears once a field changes`() =
        runViewModelTest {
            val repository =
                FakeHostWorkspaceRepository(createManualBooking = {
                    _,
                    _,
                    _,
                    ->
                    apiError(ApiErrorCodes.NO_SEATS_AVAILABLE, 409)
                })
            val vm = viewModel(repository)
            vm.fill()
            vm.addManualBooking()
            runCurrent()
            vm.editManual()
            vm.onManualParty(1)
            assertNull(
                vm.uiState.value.manual
                    ?.problem,
            )
        }

    @Test
    fun `a date no longer open refuses the booking and the list shows why`() =
        runViewModelTest {
            val repository =
                FakeHostWorkspaceRepository(createManualBooking = { _, _, _ -> apiError(ApiErrorCodes.DEPARTURE_NOT_OPEN, 409) })
            val vm = viewModel(repository)
            vm.fill()
            repository.departure = { AppResult.Success(sampleDeparture(status = HostDepartureStatus.CANCELLED)) }
            vm.addManualBooking()
            runCurrent()
            assertEquals(
                ManualProblem.NotOpen,
                vm.uiState.value.manual
                    ?.problem,
            )
            assertFalse(vm.uiState.value.open)
            // A closed date offers neither form.
            vm.closeManual()
            vm.openManualBooking("ar")
            vm.openBroadcast()
            assertNull(vm.uiState.value.manual)
            assertNull(vm.uiState.value.broadcast)
        }

    // ---------- broadcast ----------

    @Test
    fun `the broadcast is written - confirmed - then sent - never from the first step`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository()
            val vm = viewModel(repository)
            vm.openBroadcast()
            vm.reviewBroadcast()
            assertEquals(
                BroadcastProblem.Required,
                vm.uiState.value.broadcast
                    ?.problem,
            )
            vm.onBroadcastMessage("الأتوبيس هيتحرك 5 الصبح بالظبط")
            vm.sendBroadcast()
            runCurrent()
            assertTrue(repository.broadcasts.isEmpty(), "Send works from the confirm step only")
            vm.reviewBroadcast()
            assertEquals(
                BroadcastStep.Confirm,
                vm.uiState.value.broadcast
                    ?.step,
            )
            vm.sendBroadcast()
            vm.sendBroadcast()
            runCurrent()

            assertEquals(listOf("الأتوبيس هيتحرك 5 الصبح بالظبط"), repository.broadcasts, "one send, however often it is pressed")
            assertNull(vm.uiState.value.broadcast)
            assertIs<ManifestNotice.BroadcastSent>(vm.uiState.value.notice)
            assertEquals(Instant.parse("2026-11-20T18:30:00+02:00"), vm.uiState.value.broadcastLockedUntil)
            vm.openBroadcast()
            assertNull(vm.uiState.value.broadcast, "held back until nextAllowedAt")
        }

    @Test
    fun `a message over 600 characters stays in the first step`() =
        runViewModelTest {
            val vm = viewModel(FakeHostWorkspaceRepository())
            vm.openBroadcast()
            vm.onBroadcastMessage("ا".repeat(HostDepartureViewModel.BROADCAST_MAX + 1))
            vm.reviewBroadcast()
            assertEquals(
                BroadcastProblem.TooLong,
                vm.uiState.value.broadcast
                    ?.problem,
            )
            assertEquals(
                BroadcastStep.Compose,
                vm.uiState.value.broadcast
                    ?.step,
            )
        }

    @Test
    fun `the dates nextAllowedAt holds the broadcast back until it passes - then frees it by itself`() =
        runViewModelTest {
            val next = START + 10.minutes
            val repository =
                FakeHostWorkspaceRepository(
                    departure = {
                        AppResult.Success(sampleDeparture(broadcast = HostBroadcastDto("2026-11-20T09:50:00Z", 9, next.toString())))
                    },
                )
            val vm = viewModel(repository)
            assertEquals(next, vm.uiState.value.broadcastLockedUntil)
            vm.openBroadcast()
            assertNull(vm.uiState.value.broadcast)

            advanceTimeBy(10.minutes + 1.seconds)
            assertNull(vm.uiState.value.broadcastLockedUntil)
            vm.openBroadcast()
            assertTrue(vm.uiState.value.broadcast != null)
        }

    @Test
    fun `RATE_LIMITED says when from retryAfter - holds the button back and reads the list again`() =
        runViewModelTest {
            val repository =
                FakeHostWorkspaceRepository(broadcast = { _, _ -> apiError(ApiErrorCodes.RATE_LIMITED, 429, retryAfter = 600) })
            val vm = viewModel(repository)
            vm.openBroadcast()
            vm.onBroadcastMessage("نتقابل 4:45")
            vm.reviewBroadcast()
            vm.sendBroadcast()
            runCurrent()

            val problem =
                assertIs<BroadcastProblem.TooSoon>(
                    vm.uiState.value.broadcast
                        ?.problem,
                )
            assertEquals(START + 600.seconds, problem.at)
            assertEquals(START + 600.seconds, vm.uiState.value.broadcastLockedUntil)
            assertEquals(2, repository.departureReads.size)
        }

    @Test
    fun `nobody to reach and a closed date have their own words`() =
        runViewModelTest {
            val repository =
                FakeHostWorkspaceRepository(broadcast = { _, _ -> apiError(ApiErrorCodes.CONFLICT, 409, ApiErrorReasons.NO_RECIPIENTS) })
            val vm = viewModel(repository)
            vm.openBroadcast()
            vm.onBroadcastMessage("أهلا")
            vm.reviewBroadcast()
            vm.sendBroadcast()
            runCurrent()
            assertEquals(
                BroadcastProblem.NoRecipients,
                vm.uiState.value.broadcast
                    ?.problem,
            )
            assertNull(vm.uiState.value.broadcastLockedUntil, "nothing recorded: the rate limit is not used up")

            repository.broadcast = { _, _ -> apiError(ApiErrorCodes.DEPARTURE_NOT_OPEN, 409) }
            vm.sendBroadcast()
            runCurrent()
            assertEquals(
                BroadcastProblem.NotOpen,
                vm.uiState.value.broadcast
                    ?.problem,
            )
        }

    // ---------- mark paid ----------

    @Test
    fun `only an unpaid manual booking the server named can be marked paid`() =
        runViewModelTest {
            val repository = FakeHostWorkspaceRepository()
            val vm = viewModel(repository)
            val (app, unpaidManual, paidManual) = sampleTravellers()
            vm.openMarkPaid(app)
            vm.openMarkPaid(paidManual)
            assertNull(vm.uiState.value.markPaid)
            vm.openMarkPaid(unpaidManual.copy(id = null))
            assertNull(vm.uiState.value.markPaid, "no id from a server before M5-B5")

            vm.openMarkPaid(unpaidManual)
            vm.confirmMarkPaid()
            runCurrent()
            assertEquals(listOf("b2"), repository.markedPaid)
            assertNull(vm.uiState.value.markPaid)
            assertEquals(ManifestNotice.MarkedPaid("مصطفى رجب"), vm.uiState.value.notice)
        }

    @Test
    fun `a refused mark-paid stays in the sheet and reads the list again`() =
        runViewModelTest {
            val repository =
                FakeHostWorkspaceRepository(markPaid = {
                    _,
                    _,
                    ->
                    apiError(ApiErrorCodes.CONFLICT, 409, ApiErrorReasons.BOOKING_NOT_CONFIRMED)
                })
            val vm = viewModel(repository)
            vm.openMarkPaid(sampleTravellers()[1])
            vm.confirmMarkPaid()
            runCurrent()
            assertNotEquals(
                null,
                vm.uiState.value.markPaid
                    ?.error,
            )
            assertEquals(2, repository.departureReads.size)
        }

    private companion object {
        val START: Instant = Instant.parse("2026-11-20T10:00:00Z")
    }
}
