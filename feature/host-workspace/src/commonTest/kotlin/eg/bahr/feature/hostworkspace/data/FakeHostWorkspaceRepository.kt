package eg.bahr.feature.hostworkspace.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.hostworkspace.model.BroadcastReceiptDto
import eg.bahr.feature.hostworkspace.model.HostBroadcastDto
import eg.bahr.feature.hostworkspace.model.HostDepartureDto
import eg.bahr.feature.hostworkspace.model.HostDepartureStatus
import eg.bahr.feature.hostworkspace.model.HostDepartureSummaryDto
import eg.bahr.feature.hostworkspace.model.HostDepartureTripDto
import eg.bahr.feature.hostworkspace.model.HostLoginStateDto
import eg.bahr.feature.hostworkspace.model.HostMeDto
import eg.bahr.feature.hostworkspace.model.HostMoneyDto
import eg.bahr.feature.hostworkspace.model.HostTravellerDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A hand-written fake: each call answers from its lambda and is recorded, so a test sees what the screen asked for. */
internal class FakeHostWorkspaceRepository(
    initial: SessionState = SessionState.SignedIn(accountName = null, profileComplete = true, providers = emptySet()),
    var signIn: (String, String) -> AppResult<HostSignInOutcome> = { _, _ -> AppResult.Success(HostSignInOutcome.SignedIn) },
    var changePassword: (String, String) -> AppResult<Unit> = { _, _ -> AppResult.Success(Unit) },
    var me: () -> AppResult<HostMeDto> = { AppResult.Success(sampleMe()) },
    var departures: () -> AppResult<List<HostDepartureSummaryDto>> = { AppResult.Success(sampleDepartures()) },
    var departure: (String) -> AppResult<HostDepartureDto> = { AppResult.Success(sampleDeparture()) },
    var broadcast: (String, String) -> AppResult<BroadcastReceiptDto> = { _, _ ->
        AppResult.Success(BroadcastReceiptDto(9, "2026-11-20T18:00:00+02:00", "2026-11-20T18:30:00+02:00"))
    },
    var createManualBooking: (
        String,
        String,
        ManualBooking,
    ) -> AppResult<HostDepartureDto> = { _, _, _ -> AppResult.Success(sampleDeparture()) },
    var markPaid: (String, String) -> AppResult<HostDepartureDto> = { _, _ -> AppResult.Success(sampleDeparture()) },
) : HostWorkspaceRepository {
    val sessionFlow = MutableStateFlow(initial)
    override val session: StateFlow<SessionState> get() = sessionFlow

    val signIns = mutableListOf<Pair<String, String>>()
    val changes = mutableListOf<Pair<String, String>>()
    val departureReads = mutableListOf<String>()
    val broadcasts = mutableListOf<String>()

    /** (key, booking) of every manual booking sent. */
    val manualBookings = mutableListOf<Pair<String, ManualBooking>>()
    val markedPaid = mutableListOf<String>()
    var signOuts = 0

    override suspend fun loadSession(): SessionState = sessionFlow.value

    override suspend fun signIn(
        email: String,
        password: String,
    ): AppResult<HostSignInOutcome> {
        signIns += email to password
        return signIn.invoke(email, password).also {
            if (it is AppResult.Success && it.data == HostSignInOutcome.SignedIn) signInNow()
        }
    }

    override suspend fun changePassword(
        changeToken: String,
        newPassword: String,
    ): AppResult<Unit> {
        changes += changeToken to newPassword
        return changePassword.invoke(changeToken, newPassword).also { if (it is AppResult.Success) signInNow() }
    }

    override suspend fun signOut() {
        signOuts++
        sessionFlow.value = SessionState.SignedOut
    }

    fun signInNow() {
        sessionFlow.value = SessionState.SignedIn(accountName = null, profileComplete = true, providers = emptySet())
    }

    override suspend fun me(): AppResult<HostMeDto> = me.invoke()

    override suspend fun departures(): AppResult<List<HostDepartureSummaryDto>> = departures.invoke()

    override suspend fun departure(id: String): AppResult<HostDepartureDto> {
        departureReads += id
        return departure.invoke(id)
    }

    override suspend fun broadcast(
        departureId: String,
        message: String,
    ): AppResult<BroadcastReceiptDto> {
        broadcasts += message
        return broadcast.invoke(departureId, message)
    }

    override suspend fun createManualBooking(
        departureId: String,
        idempotencyKey: String,
        booking: ManualBooking,
    ): AppResult<HostDepartureDto> {
        manualBookings += idempotencyKey to booking
        return createManualBooking.invoke(departureId, idempotencyKey, booking)
    }

    override suspend fun markPaid(
        departureId: String,
        bookingId: String,
    ): AppResult<HostDepartureDto> {
        markedPaid += bookingId
        return markPaid.invoke(departureId, bookingId)
    }
}

internal fun apiError(
    code: String,
    status: Int,
    reason: String? = null,
    retryAfter: Long? = null,
) = AppResult.Failure(AppError.Api(code = code, message = "x", httpStatus = status, reason = reason, retryAfterSeconds = retryAfter))

internal fun sampleMe(
    name: String = "أشرف البحر",
    full: Boolean = true,
) = if (full) {
    HostMeDto(
        id = "9a3c1e5b-7d2f-4a6c-8e0b-1d3f5a7c9e2b",
        slug = "ashraf-el-bahr",
        name = name,
        email = "ashraf@example.com",
        kind = "person",
        role = "صاحب القارب، برج البرلس",
        phone = "+201001234567",
        verified = true,
        memberSince = "2026-09-14",
        tripsRun = 37,
        login = HostLoginStateDto(status = "ACTIVE", createdAt = "2026-10-22T11:05:00+03:00"),
    )
} else {
    HostMeDto(id = "9a3c1e5b-7d2f-4a6c-8e0b-1d3f5a7c9e2b", slug = "ashraf-el-bahr", name = name, email = "ashraf@example.com")
}

internal fun sampleDepartures() =
    listOf(
        HostDepartureSummaryDto(
            id = "3c1e5a7b-9d2f-4b6a-8c0e-2f4a6b8c0d1e",
            date = "2026-11-21",
            dayLabel = "السبت 21 نوفمبر",
            departTime = "05:00",
            status = HostDepartureStatus.OPEN,
            tripSlug = "burullus-dawn-boat",
            tripTitle = "الفجر على بحيرة البرلس",
            seatsBooked = 14,
            seatsHeld = 2,
            capacity = 18,
        ),
        HostDepartureSummaryDto(
            id = "4d2f6b8c-0e3a-4c7b-9d1f-3a5b7c9d1e2f",
            date = "2026-11-28",
            dayLabel = "السبت 28 نوفمبر",
            departTime = "05:00",
            status = HostDepartureStatus.CANCELLED,
            tripSlug = "burullus-dawn-boat",
            tripTitle = "الفجر على بحيرة البرلس",
            seatsBooked = 0,
            seatsHeld = 0,
            capacity = 18,
        ),
    )

internal fun sampleTravellers() =
    listOf(
        HostTravellerDto("b1", "BAHR-7K2M9QXA", "ندى حسن", "+201066312210", 2, true, "APP", "2026-11-08T21:40:00+02:00"),
        HostTravellerDto(
            "b2",
            "BAHR-3H8N2RTB",
            "مصطفى رجب",
            "01122994471",
            4,
            false,
            HostTravellerDto.SOURCE_MANUAL,
            "2026-11-08T18:02:00+02:00",
        ),
        HostTravellerDto(
            "b3",
            "BAHR-9P4W6YCD",
            "ياسمين وعلي",
            "01277451180",
            2,
            true,
            HostTravellerDto.SOURCE_MANUAL,
            "2026-11-07T13:55:00+02:00",
        ),
        HostTravellerDto("b4", "BAHR-5Q7E3UFG", "—", null, 1, true, "APP", "2026-11-06T22:18:00+02:00"),
    )

internal fun sampleDeparture(
    status: String = HostDepartureStatus.OPEN,
    bookings: List<HostTravellerDto> = sampleTravellers(),
    broadcast: HostBroadcastDto? = null,
    seatsHeld: Int = 2,
) = HostDepartureDto(
    id = "3c1e5a7b-9d2f-4b6a-8c0e-2f4a6b8c0d1e",
    date = "2026-11-21",
    dayLabel = "السبت 21 نوفمبر",
    status = status,
    trip = HostDepartureTripDto(slug = "burullus-dawn-boat", title = "الفجر على بحيرة البرلس", departTime = "05:00", returnTime = "22:00"),
    seatsBooked = bookings.sumOf { it.partySize },
    seatsHeld = seatsHeld,
    capacity = 18,
    collected = HostMoneyDto(amount = 6300, amountPiastres = 630000, currency = "EGP"),
    bookings = bookings,
    broadcast = broadcast,
)
