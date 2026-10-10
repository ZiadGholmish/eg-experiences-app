package eg.bahr.feature.trips.data

import eg.bahr.core.common.result.AppError
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.trips.model.BadgeDto
import eg.bahr.feature.trips.model.DepartureDto
import eg.bahr.feature.trips.model.FacetDto
import eg.bahr.feature.trips.model.HomeDto
import eg.bahr.feature.trips.model.NextDepartureDto
import eg.bahr.feature.trips.model.TripCardDto
import eg.bahr.feature.trips.model.TripCardPageDto
import eg.bahr.feature.trips.model.TripDetailDto
import eg.bahr.feature.trips.model.TripPageDto
import eg.bahr.feature.trips.model.WaitlistRequest
import kotlinx.datetime.LocalDate

/**
 * Hand-written fake: each call answers from a lambda the test sets, and every request is
 * recorded so the test can assert what the view model asked for. Unset calls fail loudly with a
 * generic error rather than returning a default that could hide a wrong call.
 *
 * Template for other features: one fake per repository interface, in that feature's commonTest.
 */
internal class FakeTripRepository(
    var listTrips: suspend (page: Int) -> AppResult<TripPageDto> = { unset() },
    /** The narrowed list (category page, search); defaults to [listTrips], which ignores the query. */
    var listFiltered: (suspend (query: TripQuery) -> AppResult<TripPageDto>)? = null,
    var sectionTrips: suspend (sectionId: String, page: Int) -> AppResult<TripCardPageDto> = { _, _ -> unset() },
    var tripBySlug: suspend (slug: String) -> AppResult<TripDetailDto> = { unset() },
    var departuresFor: suspend (slug: String) -> AppResult<List<DepartureDto>> = { unset() },
    var cards: Map<String, TripCardDto> = emptyMap(),
    var joinWaitlist: suspend (departureId: String, request: WaitlistRequest) -> AppResult<Unit> = { _, _ -> unset() },
    var home: suspend () -> AppResult<HomeDto> = { unset() },
) : TripRepository {
    val requestedPages = mutableListOf<Int>()
    val requestedQueries = mutableListOf<TripQuery>()
    val sectionRequests = mutableListOf<Pair<String, Int>>()
    var homeReads = 0
        private set

    override suspend fun home(): AppResult<HomeDto> {
        homeReads++
        return home.invoke()
    }

    val departureReads = mutableListOf<String>()
    val waitlistJoins = mutableListOf<Pair<String, WaitlistRequest>>()

    override suspend fun listTrips(
        page: Int,
        category: String?,
        filter: String?,
        q: String?,
    ): AppResult<TripPageDto> {
        val query = TripQuery(page, category, filter, q)
        requestedPages += page
        requestedQueries += query
        return listFiltered?.invoke(query) ?: listTrips.invoke(page)
    }

    override suspend fun sectionTrips(
        sectionId: String,
        page: Int,
    ): AppResult<TripCardPageDto> {
        sectionRequests += sectionId to page
        return sectionTrips.invoke(sectionId, page)
    }

    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = tripBySlug.invoke(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> {
        departureReads += slug
        return departuresFor.invoke(slug)
    }

    override suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit> {
        waitlistJoins += departureId to request
        return joinWaitlist.invoke(departureId, request)
    }

    override fun cachedCard(slug: String): TripCardDto? = cards[slug]

    private companion object {
        fun unset(): AppResult.Failure = AppResult.Failure(AppError.Unknown("FakeTripRepository: call not stubbed"))
    }
}

/** One `GET /trips` call as the fake saw it. */
internal data class TripQuery(
    val page: Int,
    val category: String? = null,
    val filter: String? = null,
    val q: String? = null,
)

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

    fun sectionPage(
        trips: List<TripCardDto>,
        page: Int = 0,
        totalPages: Int = 1,
        totalItems: Long = trips.size.toLong(),
    ) = AppResult.Success(
        TripCardPageDto(items = trips, page = page, size = trips.size, totalItems = totalItems, totalPages = totalPages),
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

    /** The seeded dawn trip's four Saturdays: 6, 2, 0 (sold out) and 11 seats left. */
    fun saturdays(): List<DepartureDto> =
        listOf(6 to "2026-10-10", 2 to "2026-10-17", 0 to "2026-10-24", 11 to "2026-10-31").mapIndexed { i, (left, date) ->
            DepartureDto(
                id = "dep-${i + 1}",
                date = LocalDate.parse(date),
                departTime = "05:00",
                returnTime = "22:00",
                seatsRemaining = left,
                capacity = 18,
                soldOut = left == 0,
                bookable = left > 0,
                unavailableReason = if (left == 0) "SOLD_OUT" else null,
                price = MoneyDto(amount = 450, currencyCode = "EGP"),
            )
        }

    fun detail(
        title: String = "الفجر على بحيرة البرلس",
        dates: List<DepartureDto> = saturdays(),
    ) = TripDetailDto(
        slug = "burullus-dawn",
        title = title,
        durationLabel = "05:00 → 22:00",
        price = MoneyDto(amount = 450, currencyCode = "EGP"),
        dates = dates,
    )
}
