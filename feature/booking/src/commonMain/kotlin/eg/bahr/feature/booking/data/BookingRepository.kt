package eg.bahr.feature.booking.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.booking.model.BookingDepartureDto
import eg.bahr.feature.booking.model.BookingTripDto
import eg.bahr.feature.booking.model.HeldBookingDto
import eg.bahr.feature.booking.model.HeldSeatsDto
import eg.bahr.feature.booking.model.PaymentStartedDto
import eg.bahr.feature.booking.model.PlaceHoldRequest
import eg.bahr.feature.booking.model.StartPaymentRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * The booking data the view models read. An interface so view-model tests can hand in a fake
 * (the project uses hand-written fakes, no mocking library).
 */
internal interface BookingRepository {
    suspend fun tripBySlug(slug: String): AppResult<BookingTripDto>

    suspend fun departuresFor(slug: String): AppResult<List<BookingDepartureDto>>

    suspend fun placeHold(request: PlaceHoldRequest): AppResult<HeldSeatsDto>

    suspend fun bookingByRef(
        ref: String,
        phone: String? = null,
    ): AppResult<HeldBookingDto>

    suspend fun heldBooking(
        ref: String,
        phone: String,
    ): AppResult<HeldBookingDto>

    suspend fun releaseHold(
        ref: String,
        phone: String,
    ): AppResult<Unit>

    suspend fun startPayment(
        ref: String,
        request: StartPaymentRequest,
    ): AppResult<PaymentStartedDto>

    /** Whether someone is signed in (M3-M1: paying needs a customer's token); the checkout fills the payer's name from it. */
    val session: StateFlow<SessionState>

    /** [session] once the secure store has been read, so a tap right after launch does not see "unknown" as signed out. */
    suspend fun currentSession(): SessionState
}

/** A pass-through for now: nothing on this screen is worth caching between visits. */
internal class DefaultBookingRepository(
    private val api: BookingApiService,
    private val authSession: AuthSession,
) : BookingRepository {
    override suspend fun tripBySlug(slug: String): AppResult<BookingTripDto> = api.tripBySlug(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<BookingDepartureDto>> = api.departuresFor(slug)

    override suspend fun placeHold(request: PlaceHoldRequest): AppResult<HeldSeatsDto> = api.placeHold(request)

    override suspend fun bookingByRef(
        ref: String,
        phone: String?,
    ): AppResult<HeldBookingDto> = api.bookingByRef(ref, phone)

    override suspend fun heldBooking(
        ref: String,
        phone: String,
    ): AppResult<HeldBookingDto> = api.heldBooking(ref, phone)

    override suspend fun releaseHold(
        ref: String,
        phone: String,
    ): AppResult<Unit> = api.releaseHold(ref, phone)

    override suspend fun startPayment(
        ref: String,
        request: StartPaymentRequest,
    ): AppResult<PaymentStartedDto> = api.startPayment(ref, request)

    override val session: StateFlow<SessionState> = authSession.state

    override suspend fun currentSession(): SessionState = authSession.load()
}
