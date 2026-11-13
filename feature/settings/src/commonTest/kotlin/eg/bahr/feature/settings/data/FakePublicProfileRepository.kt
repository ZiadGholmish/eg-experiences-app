package eg.bahr.feature.settings.data

import eg.bahr.core.catalog.model.TripCardDto
import eg.bahr.core.common.result.AppResult
import eg.bahr.core.network.MoneyDto
import eg.bahr.feature.settings.model.ProfileVisibilityDto
import eg.bahr.feature.settings.model.TravellerProfileDto
import eg.bahr.feature.settings.model.TravellerReviewDto
import eg.bahr.feature.settings.model.TravellerTripRefDto
import eg.bahr.feature.settings.model.UpdateProfileVisibilityRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate

/**
 * Profiles and switches in memory. [traveller] and [visibility] are the server's answers; each PATCH waits on the
 * next of [gates] when one is queued (so a test can land answers in any order), then answers with [update].
 */
internal class FakePublicProfileRepository(
    var traveller: suspend (String) -> AppResult<TravellerProfileDto> = { AppResult.Success(profile()) },
    var visibility: suspend () -> AppResult<ProfileVisibilityDto> = { AppResult.Success(VISIBILITY) },
) : PublicProfileRepository {
    val travellerReads = mutableListOf<String>()
    val updates = mutableListOf<UpdateProfileVisibilityRequest>()
    val gates = ArrayDeque<CompletableDeferred<AppResult<ProfileVisibilityDto>>>()

    /** The server's state, which a successful PATCH changes. */
    var server: ProfileVisibilityDto = VISIBILITY

    override suspend fun traveller(publicId: String): AppResult<TravellerProfileDto> {
        travellerReads += publicId
        return traveller.invoke(publicId)
    }

    override suspend fun visibility(): AppResult<ProfileVisibilityDto> = visibility.invoke()

    override suspend fun updateVisibility(request: UpdateProfileVisibilityRequest): AppResult<ProfileVisibilityDto> {
        updates += request
        gates.removeFirstOrNull()?.let { return it.await() }
        server =
            server.copy(
                isPublic = request.isPublic ?: server.isPublic,
                showPastTrips = request.showPastTrips ?: server.showPastTrips,
                showLikes = request.showLikes ?: server.showLikes,
                showReviews = request.showReviews ?: server.showReviews,
                showFullNameAndPhoto = request.showFullNameAndPhoto ?: server.showFullNameAndPhoto,
            )
        return AppResult.Success(server)
    }

    companion object {
        const val PUBLIC_ID = "3f9c2a7e5b1d4c8e9a0f6b2d7c4e1a95"

        /** openapi's defaults: private, reviews signed with the name. */
        val VISIBILITY = ProfileVisibilityDto(publicId = PUBLIC_ID)

        fun card(
            slug: String,
            title: String,
        ) = TripCardDto(
            slug = slug,
            title = title,
            durationLabel = "05:00 → 22:00",
            price = MoneyDto(amount = 450, currencyCode = "EGP"),
            likeCount = 12,
        )

        fun review(
            arabic: Boolean = false,
            author: String = if (arabic) "منى س." else "Mona S.",
        ) = TravellerReviewDto(
            trip = TravellerTripRefDto(slug = "dawn-burullus", title = if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus"),
            author = author,
            dateISO = LocalDate(2026, 10, 3),
            body =
                if (arabic) {
                    "رحلة هادية وجميلة، والمرشد كان فاهم كل حاجة عن الطيور."
                } else {
                    "A calm, lovely day; the guide knew every bird."
                },
            rating = 5,
            tone = "teal",
            verified = true,
        )

        fun profile(
            arabic: Boolean = false,
            pastTrips: List<TripCardDto>? =
                listOf(card("dawn-burullus", if (arabic) "الفجر على بحيرة البرلس" else "Dawn on Lake Burullus")),
            likedTrips: List<TripCardDto>? = listOf(card("rosetta-day", if (arabic) "يوم في رشيد" else "A day in Rosetta")),
            reviews: List<TravellerReviewDto>? = listOf(review(arabic)),
            name: String = if (arabic) "منى سامي" else "Mona Samy",
        ) = TravellerProfileDto(
            publicId = PUBLIC_ID,
            name = name,
            memberSince = LocalDate(2026, 3, 1),
            pastTrips = pastTrips,
            likedTrips = likedTrips,
            reviews = reviews,
        )
    }
}
