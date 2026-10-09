package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.core.network.PageDto
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.TripSummaryDto

/**
 * Hand-written fake: each call answers from a lambda the test sets, and every request is
 * recorded so the test can assert what the view model asked for. Unset calls fail loudly with a
 * generic error rather than returning a default that could hide a wrong call.
 *
 * Template for other features: one fake per repository interface, in that feature's commonTest.
 */
internal class FakeTripRepository(
    var listTrips: suspend (page: Int) -> AppResult<PageDto<TripSummaryDto>> = { unset() },
    var tripBySlug: suspend (slug: String) -> AppResult<TripDetailDto> = { unset() },
    var departuresFor: suspend (slug: String) -> AppResult<List<DepartureDto>> = { unset() },
) : TripRepository {
    val requestedPages = mutableListOf<Int>()

    override suspend fun listTrips(page: Int): AppResult<PageDto<TripSummaryDto>> {
        requestedPages += page
        return listTrips.invoke(page)
    }

    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = tripBySlug.invoke(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> = departuresFor.invoke(slug)

    private companion object {
        fun unset(): AppResult.Failure = AppResult.Failure(AppError.Unknown("FakeTripRepository: call not stubbed"))
    }
}

/** Test data. Copy is Arabic because Arabic is what most users see. */
internal object TripFixtures {
    fun trip(
        id: Long,
        title: String = "رحلة رقم $id",
        category: String? = null,
        priceEgp: Long = 450,
    ) = TripSummaryDto(
        id = id,
        slug = "trip-$id",
        category = category,
        title = title,
        pricePerPerson = MoneyDto(amount = priceEgp, currencyCode = "EGP"),
    )

    fun page(
        trips: List<TripSummaryDto>,
        page: Int = 0,
        totalPages: Int = 1,
    ) = AppResult.Success(
        PageDto(items = trips, page = page, size = trips.size, totalItems = trips.size.toLong(), totalPages = totalPages),
    )
}
