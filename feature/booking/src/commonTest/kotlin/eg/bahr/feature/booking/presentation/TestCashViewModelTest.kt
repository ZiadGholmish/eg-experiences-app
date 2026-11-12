package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.data.RecordingHoldReminderSchedule
import eg.bahr.feature.booking.di.CheckoutConfig
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.PaymentStartedDto
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Test cash on the held-seats screen (MV-M4, contract MV-B2): offered only when the server lists `test_cash`, paid at
 * once (`action.type` CONFIRMED → the confirmation, no checkout tab, no polling), and a refusal said and read again.
 * The server's list is set per test ([serverMethods]); as the real one, it is sent on a HELD booking only.
 * Never `advanceUntilIdle()` while the ticker runs: it loops until stopped.
 */
class TestCashViewModelTest {
    private val hold =
        HoldRoute(
            ref = "BRL-7K4M2X9P",
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
            totalAmount = 900,
            totalCurrency = "EGP",
            guestPhone = "+201012345678",
        )
    private val testCashHold = hold.copy(paymentMethods = BookingFixtures.WITH_TEST_CASH)

    private val placedAt = Instant.parse("2026-10-09T19:27:30Z")
    private val deviceStart = Instant.parse("2026-10-09T09:00:00Z")

    /** What the server says the booking is now, and the methods it lists while HELD. */
    private var status = "HELD"
    private var serverMethods: List<String>? = BookingFixtures.WITH_TEST_CASH

    private fun TestScope.elapsed(): Duration = testScheduler.currentTime.milliseconds

    private fun TestScope.deviceClock(): Clock =
        object : Clock {
            override fun now(): Instant = deviceStart + elapsed()
        }

    private fun TestScope.serverAnswer(): AppResult<BookingDto> =
        AppResult.Success(
            BookingFixtures.heldBooking(
                status = status,
                holdExpiresAt = (placedAt + 15.minutes).toString(),
                serverNow = (placedAt + elapsed()).toString(),
                paymentMethods = serverMethods,
            ),
        )

    private fun TestScope.repository(payment: AppResult<PaymentStartedDto> = AppResult.Success(BookingFixtures.testCashConfirmed())) =
        FakeBookingRepository(
            heldBooking = { _, _ -> serverAnswer() },
            startPayment = { _, _ -> payment },
        )

    private val reminders = RecordingHoldReminderSchedule()
    private val store = FakeActiveHoldStore(StoredHold(hold.ref, hold.guestPhone, hold.holdExpiresAt, hold.serverNow))

    private fun TestScope.started(
        repo: FakeBookingRepository,
        route: HoldRoute = testCashHold,
    ): HoldViewModel {
        val vm = HoldViewModel(route, repo, store, deviceClock(), reminders, CheckoutConfig("eg.bahr.app.dev://checkout"))
        vm.onStart()
        runCurrent()
        return vm
    }

    @Test
    fun `no list from the server - card and Vodafone Cash only - test cash cannot be picked`() =
        runViewModelTest {
            serverMethods = null
            val vm = started(repository(), route = hold)

            assertEquals(listOf(PaymentMethod.VodafoneCash, PaymentMethod.Card), vm.uiState.value.methods)
            vm.selectMethod(PaymentMethod.TestCash)
            assertEquals(PaymentMethod.VodafoneCash, vm.uiState.value.method)
            vm.onStop()
        }

    @Test
    fun `a list without test cash - not offered`() =
        runViewModelTest {
            serverMethods = listOf("card", "vodafone_cash")
            val vm = started(repository(), route = hold.copy(paymentMethods = serverMethods))

            assertFalse(PaymentMethod.TestCash in vm.uiState.value.methods)
            vm.onStop()
        }

    @Test
    fun `listed by the server - offered last - unknown methods dropped - Vodafone Cash still picked first`() =
        runViewModelTest {
            serverMethods = listOf("card", "instapay", "vodafone_cash", "test_cash")
            val vm = started(repository(), route = hold.copy(paymentMethods = serverMethods))

            val state = vm.uiState.value
            assertEquals(listOf(PaymentMethod.VodafoneCash, PaymentMethod.Card, PaymentMethod.TestCash), state.methods)
            assertEquals(PaymentMethod.VodafoneCash, state.method)
            vm.onStop()
        }

    @Test
    fun `listed only on the screen's own HELD read - offered from then on`() =
        runViewModelTest {
            // The route came from an older answer without the list; the fresh read has it.
            val vm = started(repository(), route = hold)

            assertTrue(PaymentMethod.TestCash in vm.uiState.value.methods)
            vm.onStop()
        }

    @Test
    fun `test cash pays at once - CONFIRMED goes to the confirmation with no checkout tab and no polling`() =
        runViewModelTest {
            val repo = repository()
            val vm = started(repo)
            vm.selectMethod(PaymentMethod.TestCash)
            vm.onPayerNameChange("Nada Hassan")

            vm.pay()
            runCurrent()

            val (_, request) = repo.payments.single()
            assertEquals("test_cash", request.method)
            // The server takes a payer for every method, test cash too.
            assertEquals("Nada Hassan", request.payer.name)
            assertEquals("01012345678", request.payer.phone)
            assertEquals(hold.guestPhone, request.guestPhone)

            val state = vm.uiState.value
            assertEquals(HoldPhase.Paid, state.phase)
            assertNull(state.checkoutToOpen)
            assertNull(state.checkoutUrl)
            // Bought: the stored hold (Home's card) and its reminder are gone, as for a paid read.
            assertNull(store.hold.value)
            assertTrue(hold.ref in reminders.ended)

            val readsAfter = repo.bookingReads.size
            advanceTimeBy(HoldViewModel.POLL * POLLS_WAITED)
            runCurrent()
            assertEquals(readsAfter, repo.bookingReads.size, "nothing polled after CONFIRMED")
        }

    @Test
    fun `refused - VALIDATION_FAILED says so - keeps the form and reads the booking - which drops test cash`() =
        runViewModelTest {
            val refused = AppError.Api(code = ApiErrorCodes.VALIDATION_FAILED, message = null, httpStatus = 400)
            val repo = repository(payment = AppResult.Failure(refused))
            val vm = started(repo)
            vm.selectMethod(PaymentMethod.TestCash)
            // Switched off on the server since the list was read.
            serverMethods = listOf("card", "vodafone_cash")
            val readsBefore = repo.bookingReads.size

            vm.pay()
            runCurrent()

            val state = vm.uiState.value
            assertEquals(refused, state.paymentError)
            assertEquals(PaymentStep.Choosing, state.payment)
            assertFalse(state.phase.isEnded)
            assertEquals(readsBefore + 1, repo.bookingReads.size)
            assertEquals(listOf(PaymentMethod.VodafoneCash, PaymentMethod.Card), state.methods)
            assertEquals(PaymentMethod.VodafoneCash, state.method)
            // Still held: Home's card stays.
            assertEquals(hold.ref, store.hold.value?.ref)
            vm.onStop()
        }

    @Test
    fun `a PAYMENT_PENDING read carries no list - test cash is not taken away by the polls`() =
        runViewModelTest {
            val repo = repository(payment = AppResult.Success(BookingFixtures.paymentStarted()))
            val vm = started(repo)
            vm.selectMethod(PaymentMethod.Card)
            vm.pay()
            runCurrent()

            status = "PAYMENT_PENDING"
            advanceTimeBy(HoldViewModel.POLL)
            runCurrent()

            assertEquals(PaymentStep.AwaitingCheckout, vm.uiState.value.payment)
            assertTrue(PaymentMethod.TestCash in vm.uiState.value.methods)
            vm.onStop()
        }

    private companion object {
        /** Long enough that a poll would have run a few times. */
        const val POLLS_WAITED = 5
    }
}
