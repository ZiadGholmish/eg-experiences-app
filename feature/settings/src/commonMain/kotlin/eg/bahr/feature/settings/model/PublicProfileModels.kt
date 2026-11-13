package eg.bahr.feature.settings.model

import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.ReviewDto
import eg.bahr.core.catalog.model.TripCardDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire shapes for a traveller's public profile (MP-B2, `getTraveller`) and the account's own switches
 * (`getMyProfileVisibility` / `updateMyProfileVisibility`), field for field with `../docs/api/openapi.yaml`.
 */

/**
 * openapi `TravellerProfile`. [name] is already what the owner chose to show ("Mona S." or the full name); there is no
 * account photo yet, so the page always draws its default avatar. [memberSince] is the first day of a month: shown as
 * month and year only.
 *
 * A section the owner switched off is **absent** (null here, and the page leaves it out); one switched on with nothing
 * in it is an empty list (the page shows the section with a short line). Each holds at most the server's limit (20
 * today), so there is no paging.
 */
@Serializable
internal data class TravellerProfileDto(
    val publicId: String,
    val name: String,
    val memberSince: LocalDate,
    val pastTrips: List<TripCardDto>? = null,
    val likedTrips: List<TripCardDto>? = null,
    val reviews: List<TravellerReviewDto>? = null,
)

/**
 * openapi `TravellerReview`: a `Review` plus the [trip] it is about (`allOf`, which kotlinx.serialization cannot
 * compose, so the review's fields are repeated here; [review] gives the shared card its own shape back).
 */
@Serializable
internal data class TravellerReviewDto(
    val trip: TravellerTripRefDto,
    val author: String? = null,
    val authorPublicId: String? = null,
    val dateISO: LocalDate? = null,
    val body: String? = null,
    val partyLabel: String? = null,
    val tone: String? = null,
    val rating: Int? = null,
    val photos: List<ImageDto> = emptyList(),
    val verified: Boolean = false,
) {
    val review: ReviewDto
        get() =
            ReviewDto(
                author = author,
                authorPublicId = authorPublicId,
                dateISO = dateISO,
                body = body,
                partyLabel = partyLabel,
                tone = tone,
                rating = rating,
                photos = photos,
                verified = verified,
            )
}

/** openapi `HomeTripRef`: the trip a profile review is about (open its page with [slug]). */
@Serializable
internal data class TravellerTripRefDto(
    val slug: String,
    val title: String,
    val image: ImageDto? = null,
)

/**
 * openapi `ProfileVisibility`: the signed-in account's switches. [isPublic] is the master switch (`public` on the wire,
 * off by default); [publicId] is the profile's address, for "View my public profile".
 */
@Serializable
internal data class ProfileVisibilityDto(
    val publicId: String,
    @SerialName("public") val isPublic: Boolean = false,
    val showPastTrips: Boolean = false,
    val showLikes: Boolean = false,
    val showReviews: Boolean = true,
    val showFullNameAndPhoto: Boolean = false,
)

/** openapi `UpdateProfileVisibilityRequest`: absent fields stay as they are (the client sends only the one changed). */
@Serializable
internal data class UpdateProfileVisibilityRequest(
    @SerialName("public") val isPublic: Boolean? = null,
    val showPastTrips: Boolean? = null,
    val showLikes: Boolean? = null,
    val showReviews: Boolean? = null,
    val showFullNameAndPhoto: Boolean? = null,
)
