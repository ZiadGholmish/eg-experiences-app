package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppResult
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripCardPageDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.TripPageDto
import eg.bahr.feature.trips.model.TripsSectionDto
import eg.bahr.feature.trips.model.WaitlistRequest

/**
 * The trips data the view models read. An interface so view-model tests can hand in a fake
 * (the project uses hand-written fakes, no mocking library).
 */
internal interface TripRepository {
    /**
     * One page of `GET /trips`, optionally narrowed by a [category] key, a [filter] value and the search
     * text [q] (null = none).
     */
    suspend fun listTrips(
        page: Int = 0,
        category: String? = null,
        filter: String? = null,
        q: String? = null,
    ): AppResult<TripPageDto>

    /** One page of a Home `trips` row's whole list (its "See all"). */
    suspend fun sectionTrips(
        sectionId: String,
        page: Int,
    ): AppResult<TripCardPageDto>

    /** Home's server-driven sections (banners, trip rows, categories), in the server's order. */
    suspend fun home(): AppResult<HomeDto>

    suspend fun tripBySlug(slug: String): AppResult<TripDetailDto>

    suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>>

    /** Puts a phone on a sold-out date's waiting list (capture only: no seat is offered automatically). */
    suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit>

    /**
     * The list card already loaded for [slug], if the user came from the list or a Home row. The trip page shows
     * its title and price while the full trip loads (HANDOFF: the loading state is text-first). Null
     * for a cold open, e.g. a shared link.
     */
    fun cachedCard(slug: String): TripCardDto?
}

/**
 * Remembers the cards of the pages (Home rows, "All trips", category and "See all" lists) it has loaded, for [cachedCard]; otherwise a pass-through. It is
 * the seam where real caching goes: the requirements doc calls for an offline-renderable ticket, and
 * trip detail is the other read worth holding onto between launches.
 *
 * The map is only touched from view-model coroutines, which run on the main dispatcher.
 */
internal class DefaultTripRepository(
    private val api: TripApiService,
) : TripRepository {
    private val cards = mutableMapOf<String, TripCardDto>()

    override suspend fun listTrips(
        page: Int,
        category: String?,
        filter: String?,
        q: String?,
    ): AppResult<TripPageDto> =
        api.listTrips(page = page, category = category, filter = filter, q = q).also { result ->
            if (result is AppResult.Success) remember(result.data.items)
        }

    // A "See all" list's cards are remembered too, so a trip opened from it is text-first as well.
    override suspend fun sectionTrips(
        sectionId: String,
        page: Int,
    ): AppResult<TripCardPageDto> =
        api.sectionTrips(sectionId = sectionId, page = page).also { result ->
            if (result is AppResult.Success) remember(result.data.items)
        }

    // A Home row's cards are remembered too, so a trip opened from a row is text-first like one from the list.
    override suspend fun home(): AppResult<HomeDto> =
        api.home().also { result ->
            if (result is AppResult.Success) {
                result.data.sections
                    .filterIsInstance<TripsSectionDto>()
                    .forEach { row -> remember(row.items) }
            }
        }

    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = api.tripBySlug(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> = api.departuresFor(slug)

    override suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit> = api.joinWaitlist(departureId, request)

    override fun cachedCard(slug: String): TripCardDto? = cards[slug]

    private fun remember(items: List<TripCardDto>) = items.forEach { cards[it.slug] = it }
}
