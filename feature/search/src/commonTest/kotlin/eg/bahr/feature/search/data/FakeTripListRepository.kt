package eg.bahr.feature.search.data

import eg.bahr.core.catalog.data.TripListRepository
import eg.bahr.core.catalog.model.BadgeDto
import eg.bahr.core.catalog.model.FacetDto
import eg.bahr.core.catalog.model.NextDepartureDto
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.catalog.model.TripPageDto
import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto

/**
 * Hand-written fake: each call answers from a lambda the test sets, and every request is
 * recorded so the test can assert what the view model asked for. Unset calls fail loudly with a
 * generic error rather than returning a default that could hide a wrong call.
 *
 * One fake per repository interface, in that feature's commonTest (split from feature:trips' fake, M3-M16: a fake
 * of a core:catalog type cannot live in core:testing, which may not depend on core:catalog).
 */
internal class FakeTripListRepository(
    var listTrips: suspend (page: Int) -> AppResult<TripPageDto> = { unset() },
    /** The narrowed list (category page, search, a host's trips); defaults to [listTrips], which ignores the query. */
    var listFiltered: (suspend (query: TripQuery) -> AppResult<TripPageDto>)? = null,
) : TripListRepository {
    val requestedPages = mutableListOf<Int>()
    val requestedQueries = mutableListOf<TripQuery>()

    override suspend fun listTrips(
        page: Int,
        category: String?,
        filter: String?,
        q: String?,
        host: String?,
    ): AppResult<TripPageDto> {
        val query = TripQuery(page, category, filter, q, host)
        requestedPages += page
        requestedQueries += query
        return listFiltered?.invoke(query) ?: listTrips.invoke(page)
    }

    private companion object {
        fun unset(): AppResult.Failure = AppResult.Failure(AppError.Unknown("FakeTripListRepository: call not stubbed"))
    }
}

/** One `GET /trips` call as the fake saw it. */
internal data class TripQuery(
    val page: Int,
    val category: String? = null,
    val filter: String? = null,
    val q: String? = null,
    val host: String? = null,
)

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

    fun page(
        trips: List<TripCardDto>,
        page: Int = 0,
        totalPages: Int = 1,
        facets: List<FacetDto> = emptyList(),
        totalItems: Long = trips.size.toLong(),
    ) = AppResult.Success(
        TripPageDto(items = trips, page = page, size = trips.size, totalItems = totalItems, totalPages = totalPages, facets = facets),
    )

    /** The served chips (M4-B1): the four filters, then categories, as `listTrips` orders them. */
    fun facets(
        category: String? = null,
        filter: String? = null,
        arabic: Boolean = false,
    ): List<FacetDto> {
        fun t(
            ar: String,
            en: String,
        ) = if (arabic) ar else en
        val active = filter ?: "all"
        val filters =
            listOf(
                Triple("all", t("كل الرحلات", "All trips"), "apps") to 6,
                Triple("weekend", t("الويك إند", "Weekend"), "calendar_month") to 4,
                Triple("under_400", t("أقل من 400", "Under 400"), "sell") to 0,
                Triple("half_day", t("نص يوم", "Half day"), "schedule") to 1,
            ).map { (chip, count) ->
                FacetDto(
                    type = "filter",
                    key = chip.first,
                    label = chip.second,
                    icon = chip.third,
                    count = count,
                    selected =
                        chip.first == active,
                )
            }
        val categories =
            listOf(
                listOf("on_the_boat", t("في القارب", "On the boat"), "sailing", "primary"),
                listOf("birds", t("طيور", "Birds"), "flutter_dash", "secondary"),
            ).map { (key, label, icon, tone) ->
                FacetDto(type = "category", key = key, label = label, icon = icon, tone = tone, count = 3, selected = key == category)
            }
        return filters + categories
    }
}
