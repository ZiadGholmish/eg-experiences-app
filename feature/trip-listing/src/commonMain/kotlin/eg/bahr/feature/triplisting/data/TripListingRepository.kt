package eg.bahr.feature.triplisting.data

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.triplisting.model.TripCardPageDto

/**
 * The trip lists' data: `listTrips` (`GET /trips`, the category page) comes from core:catalog's
 * [TripListRepository], which Home's repository extends too. An interface so view-model tests can hand in a
 * fake (the project uses hand-written fakes, no mocking library).
 */
internal interface TripListingRepository : TripListRepository {
    /** One page of a Home `trips` row's whole list (its "See all"). */
    suspend fun sectionTrips(
        sectionId: String,
        page: Int,
    ): AppResult<TripCardPageDto>
}

/**
 * A pass-through to [api], with `GET /trips` from [lists]. Every card loaded lands in the app's one [cards]
 * cache, so a trip opened from a list is text-first on its page.
 */
internal class DefaultTripListingRepository(
    private val api: TripListingApiService,
    private val lists: TripListRepository,
    private val cards: TripCardCache,
) : TripListingRepository,
    TripListRepository by lists {
    // A "See all" list's cards are remembered too, so a trip opened from it is text-first as well.
    override suspend fun sectionTrips(
        sectionId: String,
        page: Int,
    ): AppResult<TripCardPageDto> =
        api.sectionTrips(sectionId = sectionId, page = page).also { result ->
            if (result is AppResult.Success) cards.remember(result.data.items)
        }
}
