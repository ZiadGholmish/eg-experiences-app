package eg.bahr.feature.tripdetails.data

import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.tripdetails.model.DepartureDto
import eg.bahr.feature.tripdetails.model.TripDetailDto
import eg.bahr.feature.tripdetails.model.WaitlistRequest
import kotlinx.datetime.LocalDate

/**
 * Hand-written fake: each call answers from a lambda the test sets, and every request is
 * recorded so the test can assert what the view model asked for. Unset calls fail loudly with a
 * generic error rather than returning a default that could hide a wrong call.
 *
 * One fake per repository interface, in that feature's commonTest (split from feature:trips' fake, M3-M16: a fake
 * of a core:catalog type cannot live in core:testing, which may not depend on core:catalog).
 */
internal class FakeTripDetailRepository(
    var tripBySlug: suspend (slug: String) -> AppResult<TripDetailDto> = { unset() },
    var departuresFor: suspend (slug: String) -> AppResult<List<DepartureDto>> = { unset() },
    var cards: Map<String, TripCardDto> = emptyMap(),
    var joinWaitlist: suspend (departureId: String, request: WaitlistRequest) -> AppResult<Unit> = { _, _ -> unset() },
    /** The signed-in account's email (M3-M5); null is signed out. */
    var accountEmail: String? = null,
) : TripDetailRepository {
    val departureReads = mutableListOf<String>()
    val waitlistJoins = mutableListOf<Pair<String, WaitlistRequest>>()

    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = tripBySlug.invoke(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> {
        departureReads += slug
        return departuresFor.invoke(slug)
    }

    override suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit> {
        waitlistJoins += departureId to request
        return joinWaitlist.invoke(departureId, request)
    }

    override fun cachedCard(slug: String): TripCardDto? = cards[slug]

    override suspend fun accountEmail(): String? = accountEmail

    private companion object {
        fun unset(): AppResult.Failure = AppResult.Failure(AppError.Unknown("FakeTripDetailRepository: call not stubbed"))
    }
}

/**
 * Test data in the contract's shapes. Copy is Arabic because Arabic is what most users
 * see. No image URLs: a screenshot must not depend on whether a local MinIO happens to be running.
 */
internal object TripFixtures {
    fun trip(
        n: Int,
        title: String = "رحلة رقم $n",
        priceEgp: Long = 450,
        durationLabel: String = "05:00 → 22:00",
        badge: BadgeDto? = null,
        nextDeparture: NextDepartureDto? = null,
        nights: Int = 0,
    ) = TripCardDto(
        slug = "trip-$n",
        title = title,
        durationLabel = durationLabel,
        nights = nights,
        price = MoneyDto(amount = priceEgp, currencyCode = "EGP"),
        badge = badge,
        nextDeparture = nextDeparture,
    )

    /** The seeded dawn trip's four Saturdays: 6, 2, 0 (sold out) and 11 seats left. */
    fun saturdays(): List<DepartureDto> =
        listOf(6 to "2026-10-10", 2 to "2026-10-17", 0 to "2026-10-24", 11 to "2026-10-31").mapIndexed { i, (left, date) ->
            DepartureDto(
                id = "dep-${i + 1}",
                date = LocalDate.parse(date),
                departTime = "05:00",
                returnTime = "22:00",
                seatsRemaining = left,
                capacity = 18,
                soldOut = left == 0,
                bookable = left > 0,
                unavailableReason = if (left == 0) "SOLD_OUT" else null,
                price = MoneyDto(amount = 450, currencyCode = "EGP"),
            )
        }

    fun detail(
        title: String = "الفجر على بحيرة البرلس",
        dates: List<DepartureDto> = saturdays(),
    ) = TripDetailDto(
        slug = "burullus-dawn",
        title = title,
        durationLabel = "05:00 → 22:00",
        price = MoneyDto(amount = 450, currencyCode = "EGP"),
        dates = dates,
    )
}
