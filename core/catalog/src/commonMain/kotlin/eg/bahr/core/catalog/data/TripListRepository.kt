package eg.bahr.core.catalog.data

import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.FacetType
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.model.TripPageDto
import eg.bahr.core.common.result.AppResult

/**
 * The `GET /trips` read a [TripListPagingSource] pages through. Each feature's repository extends it
 * (feature:home's for "All trips", feature:trip-listing's, feature:host's and feature:reviews'; search reads it directly), so a view model
 * keeps one repository and its tests one fake.
 */
interface TripListRepository {
    /**
     * One page of `GET /trips`, optionally narrowed by a [category] key, a [filter] value, the search
     * text [q] and a [host]'s public slug (M4-B7) (null = none).
     */
    suspend fun listTrips(
        page: Int = 0,
        category: String? = null,
        filter: String? = null,
        q: String? = null,
        host: String? = null,
    ): AppResult<TripPageDto>

    /**
     * The kinds of trip (MV-M1, the hosts directory's category chips): `listTrips`' `category` facets, which the
     * contract serves as Home's categories (same keys, labels, icons and tones), in Home's order. Their counts
     * are trips, not hosts. A default over [listTrips] so the features' fakes need nothing new; the real
     * repository asks for one card only.
     */
    suspend fun tripCategories(): AppResult<List<FacetDto>> = listTrips().categoryFacets()
}

/** The `category` chips of a `listTrips` answer, those with a key. */
internal fun AppResult<TripPageDto>.categoryFacets(): AppResult<List<FacetDto>> =
    when (this) {
        is AppResult.Success -> AppResult.Success(data.facets.filter { it.type == FacetType.CATEGORY && !it.key.isNullOrBlank() })
        is AppResult.Failure -> this
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
        host: String?,
    ): AppResult<TripPageDto> =
        api.listTrips(page = page, category = category, filter = filter, q = q, host = host).also { result ->
            if (result is AppResult.Success) cards.remember(result.data.items)
        }

    // The facets come with any page; one card is the least the endpoint serves (size 1..50).
    override suspend fun tripCategories(): AppResult<List<FacetDto>> = api.listTrips(size = CATEGORIES_PAGE_SIZE).categoryFacets()

    private companion object {
        const val CATEGORIES_PAGE_SIZE = 1
    }
}

/**
 * The list cards loaded so far (Home rows, "All trips", category, search and "See all" lists), by
 * slug. The trip page shows a card's title and price while the full trip loads (HANDOFF: the loading
 * state is text-first), whichever feature loaded the card: one instance for the app (M4-M1c), so a
 * trip opened from Home is text-first on feature:trip-details' page. It is the seam where real caching goes.
 *
 * Only touched from view-model coroutines, which run on the main dispatcher, so it needs no lock.
 */
class TripCardCache {
    private val cards = mutableMapOf<String, TripCardDto>()

    fun remember(items: List<TripCardDto>) = items.forEach { cards[it.slug] = it }

    /** The card already loaded for [slug], or null (a cold open, e.g. a shared link). */
    fun card(slug: String): TripCardDto? = cards[slug]
}
