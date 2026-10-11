package eg.bahr.feature.trips.data

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripCardPageDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.WaitlistRequest

/**
 * The trips data the view models read. An interface so view-model tests can hand in a fake
 * (the project uses hand-written fakes, no mocking library). `listTrips` (`GET /trips`, the category
 * page and search) comes from core:catalog's [TripListRepository], which Home's repository extends too.
 */
internal interface TripRepository : TripListRepository {
    /** One page of a Home `trips` row's whole list (its "See all"). */
    suspend fun sectionTrips(
        sectionId: String,
        page: Int,
    ): AppResult<TripCardPageDto>

    suspend fun tripBySlug(slug: String): AppResult<TripDetailDto>

    suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>>

    /** Puts a phone on a sold-out date's waiting list (capture only: no seat is offered automatically). */
    suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit>

    /**
     * The list card already loaded for [slug], if the user came from a list or a Home row. The trip page shows
     * its title and price while the full trip loads (HANDOFF: the loading state is text-first). Null
     * for a cold open, e.g. a shared link.
     */
    fun cachedCard(slug: String): TripCardDto?
}

/**
 * A pass-through to [api], with `GET /trips` from [lists]. Every card loaded anywhere in the app (Home
 * rows and "All trips" in feature:home, the category, search and "See all" lists here) lands in the
 * one [cards] cache, for [cachedCard]. It is the seam where real caching goes: the requirements doc
 * calls for an offline-renderable ticket, and trip detail is the other read worth holding onto
 * between launches.
 */
internal class DefaultTripRepository(
    private val api: TripApiService,
    private val lists: TripListRepository,
    private val cards: TripCardCache,
) : TripRepository,
    TripListRepository by lists {
    // A "See all" list's cards are remembered too, so a trip opened from it is text-first as well.
    override suspend fun sectionTrips(
        sectionId: String,
        page: Int,
    ): AppResult<TripCardPageDto> =
        api.sectionTrips(sectionId = sectionId, page = page).also { result ->
            if (result is AppResult.Success) cards.remember(result.data.items)
        }

    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = api.tripBySlug(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> = api.departuresFor(slug)

    override suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit> = api.joinWaitlist(departureId, request)

    override fun cachedCard(slug: String): TripCardDto? = cards.card(slug)
}
