package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.trips.model.BadgeDto
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.NextDepartureDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.TripPageDto

/**
 * Hand-written fake: each call answers from a lambda the test sets, and every request is
 * recorded so the test can assert what the view model asked for. Unset calls fail loudly with a
 * generic error rather than returning a default that could hide a wrong call.
 *
 * Template for other features: one fake per repository interface, in that feature's commonTest.
 */
internal class FakeTripRepository(
    var listTrips: suspend (page: Int) -> AppResult<TripPageDto> = { unset() },
    var tripBySlug: suspend (slug: String) -> AppResult<TripDetailDto> = { unset() },
    var departuresFor: suspend (slug: String) -> AppResult<List<DepartureDto>> = { unset() },
) : TripRepository {
    val requestedPages = mutableListOf<Int>()

    override suspend fun listTrips(page: Int): AppResult<TripPageDto> {
        requestedPages += page
        return listTrips.invoke(page)
    }

    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = tripBySlug.invoke(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> = departuresFor.invoke(slug)

    private companion object {
        fun unset(): AppResult.Failure = AppResult.Failure(AppError.Unknown("FakeTripRepository: call not stubbed"))
    }
}

/**
 * Test data in the contract's `TripCard` shape. Copy is Arabic because Arabic is what most users
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
    ) = TripCardDto(
        slug = "trip-$n",
        title = title,
        durationLabel = durationLabel,
        price = MoneyDto(amount = priceEgp, currencyCode = "EGP"),
        badge = badge,
        nextDeparture = nextDeparture,
    )

    fun page(
        trips: List<TripCardDto>,
        page: Int = 0,
        totalPages: Int = 1,
    ) = AppResult.Success(
        TripPageDto(items = trips, page = page, size = trips.size, totalItems = trips.size.toLong(), totalPages = totalPages),
    )
}
