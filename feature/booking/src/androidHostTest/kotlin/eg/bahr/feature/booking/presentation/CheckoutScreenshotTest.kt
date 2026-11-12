package eg.bahr.feature.booking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import eg.bahr.core.common.locale.AppLanguage
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.designsystem.theme.BahrLocale
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.localization.ProvideAppLanguage
import eg.bahr.core.network.ApiErrorCodes
import eg.bahr.core.network.auth.SessionState
import eg.bahr.core.testing.captureScreenshot
import eg.bahr.feature.booking.data.BookingFixtures
import eg.bahr.feature.booking.data.FakeActiveHoldStore
import eg.bahr.feature.booking.data.FakeBookingRepository
import eg.bahr.feature.booking.di.CheckoutConfig
import eg.bahr.feature.booking.model.PaymentStartedDto
import eg.bahr.feature.booking.navigation.HoldRoute
import eg.bahr.feature.booking.presentation.components.METHOD_CARD_TAG
import eg.bahr.feature.booking.presentation.components.METHOD_TEST_CASH_TAG
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The checkout (M3-M1) × {ar, en} × {Vodafone Cash (the default), card, waiting on the payment page, declined, under a
 * minute left, the time ran out with the payment unconfirmed}, through the real view model on a fake repository and a
 * clock that only moves when the test moves it. A tall window, so the method rows, the fields and the pay bar are all
 * in one shot. M3-M3 adds the full matrix (dark, the leave-while-paying sheet, …).
 *
 * Record: `./gradlew :feature:booking:recordRoborazziAndroidHostTest`; verify: `verifyRoborazziAndroidHostTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h1180dp-xhdpi")
class CheckoutScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private var now = Instant.parse("2026-10-09T19:27:30Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = this@CheckoutScreenshotTest.now
        }

    private val hold =
        HoldRoute(
            ref = "BRL-7K4M2X9P",
            holdExpiresAt = "2026-10-09T22:42:30+03:00",
            serverNow = "2026-10-09T22:27:30+03:00",
            totalAmount = 900,
            totalCurrency = "EGP",
            guestPhone = "+201012345678",
        )

    /** What the server says the booking is now. */
    private var status = "HELD"

    @Before
    fun setUp() {
        compose.mainClock.autoAdvance = false
    }

    @Test fun vodafoneCashArabic() = snapVodafoneCash(AppLanguage.ARABIC)

    @Test fun vodafoneCashEnglish() = snapVodafoneCash(AppLanguage.ENGLISH)

    @Test fun cardArabic() = snapCard(AppLanguage.ARABIC)

    @Test fun cardEnglish() = snapCard(AppLanguage.ENGLISH)

    @Test fun awaitingArabic() = snapAwaiting(AppLanguage.ARABIC)

    @Test fun awaitingEnglish() = snapAwaiting(AppLanguage.ENGLISH)

    @Test fun declinedArabic() = snapDeclined(AppLanguage.ARABIC)

    @Test fun declinedEnglish() = snapDeclined(AppLanguage.ENGLISH)

    @Test fun expiringArabic() = snapExpiring(AppLanguage.ARABIC)

    @Test fun expiringEnglish() = snapExpiring(AppLanguage.ENGLISH)

    @Test fun lateArabic() = snapLate(AppLanguage.ARABIC)

    @Test fun lateEnglish() = snapLate(AppLanguage.ENGLISH)

    @Test fun tooLateArabic() = snapTooLate(AppLanguage.ARABIC)

    @Test fun testCashArabic() = snapTestCash(AppLanguage.ARABIC)

    @Test fun testCashEnglish() = snapTestCash(AppLanguage.ENGLISH)

    @Test fun tooLateEnglish() = snapTooLate(AppLanguage.ENGLISH)

    private fun snapVodafoneCash(language: AppLanguage) {
        show(language, viewModel(language))
        capture("checkout_vf", language)
    }

    private fun snapCard(language: AppLanguage) {
        show(language, viewModel(language))
        compose.onNodeWithTag(METHOD_CARD_TAG).performClick()
        capture("checkout_card", language)
    }

    /** Paid on the hosted checkout, its answer not in yet: "finish paying on the secure page", reopen offered. */
    private fun snapAwaiting(language: AppLanguage) {
        val vm = viewModel(language)
        show(language, vm)
        status = "PAYMENT_PENDING"
        compose.runOnUiThread { vm.pay() }
        capture("checkout_awaiting", language)
    }

    /** The provider refused to start it: the form stays, saying why. */
    private fun snapDeclined(language: AppLanguage) {
        val declined = AppError.Api(code = ApiErrorCodes.PAYMENT_DECLINED, message = null, httpStatus = 402)
        val vm = viewModel(language, payment = AppResult.Failure(declined))
        show(language, vm)
        compose.runOnUiThread { vm.pay() }
        capture("checkout_declined", language)
    }

    /** 00:42 left: the pay button still works, the countdown in its last-minute colour. */
    private fun snapExpiring(language: AppLanguage) {
        val vm = viewModel(language)
        now += 14.minutes + 18.seconds
        show(language, vm)
        capture("checkout_expiring", language)
    }

    /** The deadline passed with the payment unconfirmed: still waiting, late money can confirm it or is refunded. */
    private fun snapLate(language: AppLanguage) {
        val vm = viewModel(language)
        show(language, vm)
        status = "EXPIRED"
        compose.runOnUiThread { vm.pay() }
        compose.waitForIdle()
        now += 16.minutes
        compose.runOnUiThread { vm.onCheckoutClosed() }
        capture("checkout_late", language)
    }

    /** Under the server's last minute (`HOLD_EXPIRED` from the start): the form stays, saying it is too late to pay. */
    private fun snapTooLate(language: AppLanguage) {
        val tooLate = AppError.Api(code = ApiErrorCodes.HOLD_EXPIRED, message = null, httpStatus = 410)
        val vm = viewModel(language, payment = AppResult.Failure(tooLate))
        now += 14.minutes + 30.seconds
        show(language, vm)
        compose.runOnUiThread { vm.pay() }
        capture("checkout_too_late", language)
    }

    /**
     * A server that takes test cash (MV-M4): its row last, with the "Test" badge, picked; the fields are the full name and
     * a mobile number, and the pay button says the total.
     */
    private fun snapTestCash(language: AppLanguage) {
        val route = hold.copy(paymentMethods = BookingFixtures.WITH_TEST_CASH)
        show(language, viewModel(language, route = route, paymentMethods = BookingFixtures.WITH_TEST_CASH), route)
        compose.onNodeWithTag(METHOD_TEST_CASH_TAG).performClick()
        capture("checkout_test_cash", language)
    }

    private fun viewModel(
        language: AppLanguage,
        payment: AppResult<PaymentStartedDto> = AppResult.Success(BookingFixtures.paymentStarted()),
        route: HoldRoute = hold,
        paymentMethods: List<String>? = null,
    ): HoldViewModel {
        val arabic = language == AppLanguage.ARABIC
        val repository =
            FakeBookingRepository(
                heldBooking = { _, _ ->
                    AppResult.Success(
                        BookingFixtures.heldBooking(
                            status = status,
                            serverNow = (Instant.parse("2026-10-09T19:27:30Z") + (now - START)).toString(),
                            title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus",
                            dayLabel = if (arabic) "السبت 10 أكتوبر" else "Sat 10 Oct",
                            city = if (arabic) "القاهرة" else "Cairo",
                            placeName = if (arabic) "عبد المنعم رياض" else "Abdel Moneim Riad",
                            paymentMethods = paymentMethods,
                        ),
                    )
                },
                startPayment = { _, _ -> payment },
            ).apply {
                session.value =
                    SessionState.SignedIn(
                        accountName = if (arabic) "ندى حسن" else "Nada Hassan",
                        profileComplete = true,
                        providers = setOf("google"),
                    )
            }
        return HoldViewModel(route, repository, FakeActiveHoldStore(), clock, checkout = CheckoutConfig("eg.bahr.app://checkout"))
    }

    private fun show(
        language: AppLanguage,
        viewModel: HoldViewModel,
        route: HoldRoute = hold,
    ) {
        compose.setContent {
            ProvideAppLanguage(language) {
                BahrTheme(locale = if (language == AppLanguage.ARABIC) BahrLocale.Arabic else BahrLocale.English) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        // The hosted checkout never opens in a test: the browser hands straight back.
                        HoldScreen(hold = route, onEnded = {}, viewModel = viewModel, browser = { _, _ -> })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun capture(
        prefix: String,
        language: AppLanguage,
    ) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        compose.captureScreenshot("${prefix}_${language.tag}")
    }

    private companion object {
        const val SETTLE_MS = 1_000L
        val START: Instant = Instant.parse("2026-10-09T19:27:30Z")
    }
}
