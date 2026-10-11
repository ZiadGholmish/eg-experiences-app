package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.model.TripPageDto
import eg.bahr.core.common.result.AppResult

/**
 * The `GET /trips` read a [TripListPagingSource] pages through. Each feature's repository extends it
 * (feature:home's for "All trips", feature:trips' for the category page and search), so a view model
 * keeps one repository and its tests one fake.
 */
interface TripListRepository {
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
}

/** Reads [TripListApiService] and remembers every card it loads in [cards]. */
internal class DefaultTripListRepository(
    private val api: TripListApiService,
    private val cards: TripCardCache,
) : TripListRepository {
    override suspend fun listTrips(
        page: Int,
        category: String?,
        filter: String?,
        q: String?,
    ): AppResult<TripPageDto> =
        api.listTrips(page = page, category = category, filter = filter, q = q).also { result ->
            if (result is AppResult.Success) cards.remember(result.data.items)
        }
}

/**
 * The list cards loaded so far (Home rows, "All trips", category, search and "See all" lists), by
 * slug. The trip page shows a card's title and price while the full trip loads (HANDOFF: the loading
 * state is text-first), whichever feature loaded the card: one instance for the app (M4-M1c), so a
 * trip opened from Home is text-first on feature:trips' page. It is the seam where real caching goes.
 *
 * Only touched from view-model coroutines, which run on the main dispatcher, so it needs no lock.
 */
class TripCardCache {
    private val cards = mutableMapOf<String, TripCardDto>()

    fun remember(items: List<TripCardDto>) = items.forEach { cards[it.slug] = it }

    /** The card already loaded for [slug], or null (a cold open, e.g. a shared link). */
    fun card(slug: String): TripCardDto? = cards[slug]
}
