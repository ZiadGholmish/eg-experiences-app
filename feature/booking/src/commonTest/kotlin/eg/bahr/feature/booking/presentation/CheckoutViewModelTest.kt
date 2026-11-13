package eg.bahr.feature.booking.presentation

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.datastore.StoredHold
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.runViewModelTest
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.data.RecordingHoldReminderSchedule
import eg.bahr.feature.booking.di.CheckoutConfig
import eg.bahr.feature.booking.model.BookingDto
import eg.bahr.feature.booking.model.PaymentStartedDto
import eg.bahr.feature.booking.navigation.HoldRoute
import kotlinx.coroutines.CompletableDeferred
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
 * The checkout on the held-seats screen (M3-M1): the form, the sign-in gate, `startPayment`'s answers and the polling
 * that follows, against a server whose booking status the test sets ([status]) and a clock that moves with virtual
 * time. Never `advanceUntilIdle()` while the ticker runs: it loops until stopped.
 */
class CheckoutViewModelTest {
    private val hold =
        HoldRoute(
            ref = "BRL-7K4M2X9P",
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
            totalAmount = 900,
            totalCurrency = "EGP",
            guestPhone = "+201012345678",
        )

    private val placedAt = Instant.parse("2026-10-09T19:27:30Z")
    private val deviceStart = Instant.parse("2026-10-09T09:00:00Z")

    /** What the server says the booking is now. */
    private var status = "HELD"

    private fun TestScope.elapsed(): Duration = testScheduler.currentTime.milliseconds

    private fun TestScope.deviceClock(): Clock =
        object : Clock {
            override fun now(): Instant = deviceStart + elapsed()
        }

    /** The booking as the server reads it now: HELD / PAYMENT_PENDING keep the 15-minute deadline, even once past. */
    private fun TestScope.serverAnswer(): AppResult<BookingDto> =
        AppResult.Success(
            BookingFixtures.heldBooking(
                status = status,
                holdExpiresAt = (placedAt + 15.minutes).toString(),
                serverNow = (placedAt + elapsed()).toString(),
            ),
        )

    private fun TestScope.repository(signedIn: Boolean = true) =
        FakeBookingRepository(
            heldBooking = { _, _ -> serverAnswer() },
            startPayment = { _, _ -> AppResult.Success(BookingFixtures.paymentStarted()) },
            signedIn = signedIn,
        )

    private val reminders = RecordingHoldReminderSchedule()
    private val store = FakeActiveHoldStore(StoredHold(hold.ref, hold.guestPhone, hold.holdExpiresAt, hold.serverNow))

    private fun TestScope.started(
        repo: FakeBookingRepository,
        returnUrl: String? = "eg.bahr.app.dev://checkout",
    ): HoldViewModel {
        val vm = HoldViewModel(hold, repo, store, deviceClock(), reminders, CheckoutConfig(returnUrl))
        vm.onStart()
        runCurrent()
        return vm
    }

    @Test
    fun `Vodafone Cash is picked first - with the hold's phone and the account's name filled in`() =
        runViewModelTest {
            val vm = started(repository())

            val state = vm.uiState.value
            assertEquals(PaymentMethod.VodafoneCash, state.method)
            assertEquals("01012345678", state.payer.phone)
            assertEquals("Nada Hassan", state.payer.name)
            assertEquals(PaymentStep.Choosing, state.payment)
            assertTrue(state.canPay)
            vm.onStop()
        }

    @Test
    fun `a wrong field stops the pay tap and says so`() =
        runViewModelTest {
            val repo = repository()
            val vm = started(repo)
            vm.onPayerPhoneChange("0101")

            vm.pay()
            runCurrent()

            assertTrue(vm.uiState.value.showPayerErrors)
            assertTrue(repo.payments.isEmpty())
            vm.onStop()
        }

    @Test
    fun `signed out - paying opens the sign-in sheet first and charges nothing`() =
        runViewModelTest {
            val repo = repository(signedIn = false)
            val vm = started(repo)
            vm.onPayerNameChange("Nada Hassan")

            vm.pay()
            runCurrent()

            assertTrue(vm.uiState.value.signInRequested)
            assertEquals(PaymentStep.Choosing, vm.uiState.value.payment)
            assertTrue(repo.payments.isEmpty())

            // Back from the sheet, signed in: the next tap pays.
            vm.onSignInShown()
            repo.session.value = FakeBookingRepository.SIGNED_IN
            vm.pay()
            runCurrent()
            assertEquals(1, repo.payments.size)
            vm.onStop()
        }

    @Test
    fun `paying sends the contract's body and opens the hosted checkout`() =
        runViewModelTest {
            val repo = repository()
            val vm = started(repo)
            vm.selectMethod(PaymentMethod.Card)
            vm.onPayerNameChange("  Nada Hassan ")
            vm.onPayerPhoneChange("+20 101 234 5678")

            vm.pay()
            runCurrent()

            val (ref, request) = repo.payments.single()
            assertEquals(hold.ref, ref)
            assertEquals("card", request.method)
            assertEquals("Nada Hassan", request.payer.name)
            assertEquals("01012345678", request.payer.phone)
            assertEquals(hold.guestPhone, request.guestPhone)
            assertEquals("eg.bahr.app.dev://checkout/${hold.ref}", request.returnUrl)

            val state = vm.uiState.value
            assertEquals(PaymentStep.AwaitingCheckout, state.payment)
            assertEquals(BookingFixtures.paymentStarted().action.url, state.checkoutToOpen)
            // No "pay before the seats are released" while paying.
            assertTrue(hold.ref in reminders.ended)
            vm.onStop()
        }

    @Test
    fun `a build without a return link sends none`() =
        runViewModelTest {
            val repo = repository()
            val vm = started(repo, returnUrl = null)

            vm.pay()
            runCurrent()

            assertNull(
                repo.payments
                    .single()
                    .second.returnUrl,
            )
            vm.onStop()
        }

    @Test
    fun `waits through PAYMENT_PENDING - past the deadline too - until the booking reads paid`() =
        runViewModelTest {
            val repo = repository()
            val vm = started(repo)
            vm.pay()
            runCurrent()
            status = "PAYMENT_PENDING"
            vm.onCheckoutClosed()
            runCurrent()
            assertEquals(PaymentStep.AwaitingCheckout, vm.uiState.value.payment)

            // The visible deadline passes while the server's grace keeps the seats: no "time is up", still polling.
            val readsBefore = repo.bookingReads.size
            advanceTimeBy(16.minutes)
            runCurrent()
            assertEquals(HoldPhase.Holding, vm.uiState.value.phase)
            assertEquals(PaymentStep.AwaitingCheckout, vm.uiState.value.payment)
            assertTrue(repo.bookingReads.size > readsBefore + 100, "polled every few seconds")

            status = "CONFIRMED"
            advanceTimeBy(HoldViewModel.POLL)
            runCurrent()
            assertEquals(HoldPhase.Paid, vm.uiState.value.phase)
            assertTrue(vm.uiState.value.phase.isEnded)
            assertNull(store.hold.value)
        }

    @Test
    fun `HELD again after paying means the provider declined - the form is back and says so`() =
        runViewModelTest {
            val vm = started(repository())
            vm.pay()
            runCurrent()

            advanceTimeBy(HoldViewModel.POLL)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(PaymentStep.Choosing, state.payment)
            assertTrue(state.declined)
            assertNull(state.checkoutUrl)
            assertTrue(state.canPay)
            vm.onStop()
        }

    @Test
    fun `EXPIRED after paying is not the end - late money can still confirm it`() =
        runViewModelTest {
            val vm = started(repository())
            vm.pay()
            runCurrent()

            status = "EXPIRED"
            advanceTimeBy(HoldViewModel.POLL)
            runCurrent()
            assertEquals(PaymentStep.Late, vm.uiState.value.payment)
            assertFalse(vm.uiState.value.phase.isEnded)
            // Kept: Home's card is still the way back to it.
            assertEquals(hold.ref, store.hold.value?.ref)

            status = "CONFIRMED"
            advanceTimeBy(HoldViewModel.LATE_POLL)
            runCurrent()
            assertEquals(HoldPhase.Paid, vm.uiState.value.phase)
        }

    @Test
    fun `Vodafone Cash asked on the phone has nothing to open`() =
        runViewModelTest {
            val repo =
                repository().apply {
                    startPayment =
                        { _, _ -> AppResult.Success(BookingFixtures.paymentStarted(walletPending = true)) }
                }
            val vm = started(repo)

            vm.pay()
            runCurrent()

            assertEquals(PaymentStep.AwaitingWallet, vm.uiState.value.payment)
            assertNull(vm.uiState.value.checkoutToOpen)
            vm.onStop()
        }

    @Test
    fun `a payment refused at the start keeps the seats and says why`() =
        runViewModelTest {
            val declined = AppError.Api(code = ApiErrorCodes.PAYMENT_DECLINED, message = null, httpStatus = 402)
            val repo = repository().apply { startPayment = { _, _ -> AppResult.Failure(declined) } }
            val vm = started(repo)

            vm.pay()
            runCurrent()

            assertEquals(PaymentStep.Choosing, vm.uiState.value.payment)
            assertEquals(declined, vm.uiState.value.paymentError)
            assertTrue(vm.uiState.value.canPay)
            vm.onStop()
        }

    @Test
    fun `a refused token asks for the sign-in again`() =
        runViewModelTest {
            val unauthorized = AppError.Api(code = ApiErrorCodes.UNAUTHORIZED, message = null, httpStatus = 401)
            val repo = repository().apply { startPayment = { _, _ -> AppResult.Failure(unauthorized) } }
            val vm = started(repo)

            vm.pay()
            runCurrent()

            assertTrue(vm.uiState.value.signInRequested)
            assertNull(vm.uiState.value.paymentError)
            vm.onStop()
        }

    @Test
    fun `CONFLICT reads the booking - a payment already under way is waited for`() =
        runViewModelTest {
            val conflict = AppError.Api(code = ApiErrorCodes.CONFLICT, message = null, httpStatus = 409)
            val repo = repository().apply { startPayment = { _, _ -> AppResult.Failure(conflict) } }
            val vm = started(repo)
            status = "PAYMENT_PENDING"

            vm.pay()
            runCurrent()

            assertEquals(PaymentStep.Confirming, vm.uiState.value.payment)
            vm.onStop()
        }

    @Test
    fun `reopened while PAYMENT_PENDING it waits - with no reminder`() =
        runViewModelTest {
            status = "PAYMENT_PENDING"
            val vm = started(repository())

            assertEquals(PaymentStep.Confirming, vm.uiState.value.payment)
            assertFalse(vm.uiState.value.canPay)
            assertTrue(hold.ref in reminders.ended)
            assertTrue(reminders.held.isEmpty())
            vm.onStop()
        }

    @Test
    fun `leaving while paying releases nothing`() =
        runViewModelTest {
            val repo = repository()
            val vm = started(repo)
            vm.pay()
            runCurrent()

            vm.requestLeave()
            vm.confirmLeave()
            runCurrent()

            assertTrue(repo.releases.isEmpty())
            assertEquals(HoldPhase.LeftUnreleased, vm.uiState.value.phase)
            assertEquals(hold.ref, store.hold.value?.ref)
        }

    @Test
    fun `a release refused with CONFLICT does not offer to leave anyway - it reads the booking`() =
        runViewModelTest {
            val conflict = AppError.Api(code = ApiErrorCodes.CONFLICT, message = null, httpStatus = 409)
            val repo = repository().apply { releaseHold = { _, _ -> AppResult.Failure(conflict) } }
            val vm = started(repo)
            status = "PAYMENT_PENDING"

            vm.requestLeave()
            vm.confirmLeave()
            runCurrent()

            val state = vm.uiState.value
            assertNull(state.releaseError)
            assertFalse(state.isLeaveConfirmVisible)
            assertEquals(PaymentStep.Confirming, state.payment)
            vm.onStop()
        }

    @Test
    fun `the session's name does not overwrite a typed one`() =
        runViewModelTest {
            val repo = repository(signedIn = false)
            val vm = started(repo)
            vm.onPayerNameChange("Omar")

            repo.session.value = SessionState.SignedIn(accountName = "Nada Hassan", profileComplete = true, providers = emptySet())
            runCurrent()

            assertEquals("Omar", vm.uiState.value.payer.name)
            vm.onStop()
        }

    @Test
    fun `the method cannot change while a payment starts`() =
        runViewModelTest {
            val vm = started(repository())
            vm.pay()
            // Starting: the answer has not come back yet.
            vm.selectMethod(PaymentMethod.Card)
            assertEquals(PaymentMethod.VodafoneCash, vm.uiState.value.method)
            runCurrent()
            vm.onStop()
        }

    @Test
    fun `a read sent before paying cannot pass for a decline once the payment has started`() =
        runViewModelTest {
            val late = CompletableDeferred<AppResult<BookingDto>>()
            var first = true
            val repo =
                repository().apply {
                    heldBooking = { _, _ ->
                        if (first) {
                            first = false
                            late.await()
                        } else {
                            serverAnswer()
                        }
                    }
                }
            val vm = started(repo)

            // The start's own read is still out when the person pays.
            vm.pay()
            runCurrent()
            assertEquals(PaymentStep.AwaitingCheckout, vm.uiState.value.payment)
            late.complete(serverAnswer())
            runCurrent()

            val state = vm.uiState.value
            assertEquals(PaymentStep.AwaitingCheckout, state.payment)
            assertFalse(state.declined)
            assertEquals(BookingFixtures.paymentStarted().action.url, state.checkoutUrl)

            // The first poll after the start does read, and a PAYMENT_PENDING keeps waiting.
            status = "PAYMENT_PENDING"
            vm.onCheckoutClosed()
            runCurrent()
            assertEquals(PaymentStep.AwaitingCheckout, vm.uiState.value.payment)
            vm.onStop()
        }

    @Test
    fun `a read sent while the payment starts cannot pass for a decline either`() =
        runViewModelTest {
            val start = CompletableDeferred<AppResult<PaymentStartedDto>>()
            val heldBeforeStart = CompletableDeferred<AppResult<BookingDto>>()
            var reads = 0
            val repo =
                repository().apply {
                    startPayment = { _, _ -> start.await() }
                    heldBooking = { _, _ ->
                        reads++
                        // The second read is the one the screen sends coming back to the front mid-start.
                        if (reads == 2) heldBeforeStart.await() else serverAnswer()
                    }
                }
            val vm = started(repo)

            vm.pay()
            runCurrent()
            assertEquals(PaymentStep.Starting, vm.uiState.value.payment)
            // The person switched apps and came back while the provider was slow: the screen starts again.
            vm.onStop()
            vm.onStart()
            runCurrent()
            start.complete(AppResult.Success(BookingFixtures.paymentStarted()))
            runCurrent()
            heldBeforeStart.complete(serverAnswer())
            runCurrent()

            val state = vm.uiState.value
            assertEquals(PaymentStep.AwaitingCheckout, state.payment)
            assertFalse(state.declined)
            assertEquals(BookingFixtures.paymentStarted().action.url, state.checkoutToOpen)
            vm.onStop()
        }

    @Test
    fun `Back while the payment starts offers nothing to release`() =
        runViewModelTest {
            val start = CompletableDeferred<AppResult<PaymentStartedDto>>()
            val repo = repository().apply { startPayment = { _, _ -> start.await() } }
            val vm = started(repo)

            vm.pay()
            runCurrent()
            vm.requestLeave()

            assertFalse(vm.uiState.value.isLeaveConfirmVisible)
            assertTrue(repo.releases.isEmpty())
            start.complete(AppResult.Success(BookingFixtures.paymentStarted()))
            runCurrent()
            // Once it has started, Back offers the paying sheet: leave, nothing released.
            vm.requestLeave()
            assertTrue(vm.uiState.value.isLeaveConfirmVisible)
            vm.onStop()
        }

    @Test
    fun `too little time left to start says so and reads the booking`() =
        runViewModelTest {
            val tooLate = AppError.Api(code = ApiErrorCodes.HOLD_EXPIRED, message = null, httpStatus = 410)
            val repo = repository().apply { startPayment = { _, _ -> AppResult.Failure(tooLate) } }
            val vm = started(repo)
            val readsBefore = repo.bookingReads.size
            status = "EXPIRED"

            vm.pay()
            runCurrent()

            assertEquals(tooLate, vm.uiState.value.paymentError)
            assertEquals(readsBefore + 1, repo.bookingReads.size)
            // Not started, so EXPIRED is the end of the hold, not Late.
            assertEquals(HoldPhase.Expired, vm.uiState.value.phase)
        }

    @Test
    fun `a date called off says so and reads the booking`() =
        runViewModelTest {
            val calledOff = AppError.Api(code = ApiErrorCodes.DEPARTURE_NOT_OPEN, message = null, httpStatus = 409)
            val repo = repository().apply { startPayment = { _, _ -> AppResult.Failure(calledOff) } }
            val vm = started(repo)
            val readsBefore = repo.bookingReads.size

            vm.pay()
            runCurrent()

            assertEquals(calledOff, vm.uiState.value.paymentError)
            assertEquals(PaymentStep.Choosing, vm.uiState.value.payment)
            assertEquals(readsBefore + 1, repo.bookingReads.size)
            vm.onStop()
        }

    @Test
    fun `a token that is not a customer's asks for the sign-in`() =
        runViewModelTest {
            val notCustomer = AppError.Api(code = ApiErrorCodes.IDENTITY_REQUIRED, message = null, httpStatus = 401)
            val repo = repository().apply { startPayment = { _, _ -> AppResult.Failure(notCustomer) } }
            val vm = started(repo)

            vm.pay()
            runCurrent()

            assertTrue(vm.uiState.value.signInRequested)
            assertEquals(PaymentStep.Choosing, vm.uiState.value.payment)
            assertNull(vm.uiState.value.paymentError)
            vm.onStop()
        }

    @Test
    fun `a number Vodafone Cash refuses keeps the seats - and another method can pay`() =
        runViewModelTest {
            // The app checks only for an Egyptian mobile; whether the number has a Vodafone Cash wallet is the
            // provider's call, answered PAYMENT_DECLINED at the start (openapi startPayment 402).
            val notAWallet = AppError.Api(code = ApiErrorCodes.PAYMENT_DECLINED, message = null, httpStatus = 402)
            var answer: AppResult<PaymentStartedDto> = AppResult.Failure(notAWallet)
            val repo = repository().apply { startPayment = { _, _ -> answer } }
            val vm = started(repo)
            vm.onPayerPhoneChange("01112345678")

            vm.pay()
            runCurrent()

            assertEquals(
                "vodafone_cash",
                repo.payments
                    .single()
                    .second.method,
            )
            assertEquals(
                "01112345678",
                repo.payments
                    .single()
                    .second.payer.phone,
            )
            assertEquals(PaymentStep.Choosing, vm.uiState.value.payment)
            assertEquals(notAWallet, vm.uiState.value.paymentError)
            assertFalse(vm.uiState.value.declined)

            vm.selectMethod(PaymentMethod.Card)
            assertNull(vm.uiState.value.paymentError)
            assertTrue(vm.uiState.value.canPay)
            answer = AppResult.Success(BookingFixtures.paymentStarted())
            vm.pay()
            runCurrent()

            assertEquals(
                "card",
                repo.payments
                    .last()
                    .second.method,
            )
            assertEquals(PaymentStep.AwaitingCheckout, vm.uiState.value.payment)
            vm.onStop()
        }
}
