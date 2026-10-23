package eg.bahr.feature.home.data

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.home.model.HomeDto
import eg.bahr.feature.home.model.TripsSectionDto

/**
 * What Home reads: its server-driven sections, and "All trips" (`GET /trips`, from core:catalog's
 * [TripListRepository]). An interface so view-model tests can hand in a fake.
 */
internal interface HomeRepository : TripListRepository {
    /** Home's server-driven sections (banners, trip rows, categories), in the server's order. */
    suspend fun home(): AppResult<HomeDto>
}

/**
 * [api] for the sections, [lists] for "All trips". A Home row's cards go into the app's one [cards]
 * cache (core:catalog), as the list's do, so a trip opened from a row is text-first on the trip page
 * (feature:trip-details) like one from the list.
 */
internal class DefaultHomeRepository(
    private val api: HomeApiService,
    private val lists: TripListRepository,
    private val cards: TripCardCache,
) : HomeRepository,
    TripListRepository by lists {
    override suspend fun home(): AppResult<HomeDto> =
        api.home().also { result ->
            if (result is AppResult.Success) {
                result.data.sections
                    .filterIsInstance<TripsSectionDto>()
                    .forEach { row -> cards.remember(row.items) }
            }
        }
}
