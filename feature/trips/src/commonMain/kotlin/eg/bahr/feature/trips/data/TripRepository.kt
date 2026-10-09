package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.TripPageDto
import eg.bahr.feature.trips.model.WaitlistRequest

/**
 * The trips data the view models read. An interface so view-model tests can hand in a fake
 * (the project uses hand-written fakes, no mocking library).
 */
internal interface TripRepository {
    suspend fun listTrips(page: Int = 0): AppResult<TripPageDto>

    suspend fun tripBySlug(slug: String): AppResult<TripDetailDto>

    suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>>

    /** Puts a phone on a sold-out date's waiting list (capture only: no seat is offered automatically). */
    suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit>

    /**
     * The list card already loaded for [slug], if the user came from the list. The trip page shows
     * its title and price while the full trip loads (HANDOFF: the loading state is text-first). Null
     * for a cold open, e.g. a shared link.
     */
    fun cachedCard(slug: String): TripCardDto?
}

/**
 * Remembers the cards of the pages it has loaded, for [cachedCard]; otherwise a pass-through. It is
 * the seam where real caching goes: the requirements doc calls for an offline-renderable ticket, and
 * trip detail is the other read worth holding onto between launches.
 *
 * The map is only touched from view-model coroutines, which run on the main dispatcher.
 */
internal class DefaultTripRepository(
    private val api: TripApiService,
) : TripRepository {
    private val cards = mutableMapOf<String, TripCardDto>()

    override suspend fun listTrips(page: Int): AppResult<TripPageDto> =
        api.listTrips(page = page).also { result ->
            if (result is AppResult.Success) result.data.items.forEach { cards[it.slug] = it }
        }

    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = api.tripBySlug(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> = api.departuresFor(slug)

    override suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit> = api.joinWaitlist(departureId, request)

    override fun cachedCard(slug: String): TripCardDto? = cards[slug]
}
