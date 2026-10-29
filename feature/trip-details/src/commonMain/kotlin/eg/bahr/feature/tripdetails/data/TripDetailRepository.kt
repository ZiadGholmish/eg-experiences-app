package eg.bahr.feature.tripdetails.data

import eg.bahr.core.catalog.data.TripCardCache
import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.auth.AuthSession
import eg.bahr.core.network.auth.SessionState
import eg.bahr.feature.tripdetails.model.DepartureDto
import eg.bahr.feature.tripdetails.model.TripDetailDto
import eg.bahr.feature.tripdetails.model.WaitlistRequest

/**
 * The trip data the trip page reads. An interface so view-model tests can hand in a fake (the project
 * uses hand-written fakes, no mocking library).
 */
internal interface TripDetailRepository {
    suspend fun tripBySlug(slug: String): AppResult<TripDetailDto>

    suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>>

    /** Puts an email on a sold-out date's waiting list (capture only: no seat is offered automatically). */
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

    /**
     * The signed-in account's email, for the waiting-list form (M3-M5: "signed-in users get the waitlist email
     * prefilled"); null when signed out, when the account has none, or when it could not be read (the form then
     * stays empty, as it is signed out).
     */
    suspend fun accountEmail(): String?
}

/**
 * A pass-through to [api]. Every card loaded anywhere in the app (Home rows and "All trips" in feature:home, the
 * category and "See all" lists in feature:trip-listing, search, the host's trips) lands in core:catalog's one
 * [cards] cache, for [cachedCard]. It is the seam where real caching goes: the requirements doc calls for an
 * offline-renderable ticket, and trip detail is the other read worth holding onto between launches.
 */
internal class DefaultTripDetailRepository(
    private val api: TripDetailApiService,
    private val cards: TripCardCache,
    private val session: AuthSession,
) : TripDetailRepository {
    override suspend fun tripBySlug(slug: String): AppResult<TripDetailDto> = api.tripBySlug(slug)

    override suspend fun departuresFor(slug: String): AppResult<List<DepartureDto>> = api.departuresFor(slug)

    override suspend fun joinWaitlist(
        departureId: String,
        request: WaitlistRequest,
    ): AppResult<Unit> = api.joinWaitlist(departureId, request)

    override fun cachedCard(slug: String): TripCardDto? = cards.card(slug)

    override suspend fun accountEmail(): String? {
        if (session.load() !is SessionState.SignedIn) return null
        return (api.me() as? AppResult.Success)?.data?.email?.takeIf { it.isNotBlank() }
    }
}
