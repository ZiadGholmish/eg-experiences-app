package eg.bahr.feature.trips.model

import eg.bahr.core.catalog.model.ImageDto
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Wire shapes for `GET /api/v1/hosts/{slug}` (`getHost`, M4-B7), field for field with
 * `../docs/api/openapi.yaml`. The host's trips are not here: they are `GET /trips?host=` (core:catalog's
 * list, paged). Never a phone number or a WhatsApp link: those come with a paid booking only (D4).
 */

/**
 * openapi `HostProfile`: the host page. [kind] stays a string (`person` / `company`) so a value this
 * build does not know reads as a person, as the contract asks, instead of failing the page's decode.
 * [memberSince] is the Cairo date the host was first approved. [reviews] are the newest few, in the
 * trip page's `Review` shape.
 */
@Serializable
internal data class HostProfileDto(
    val slug: String,
    val kind: String = HostKind.PERSON,
    val name: String,
    val avatar: ImageDto? = null,
    val role: String? = null,
    val bio: String? = null,
    val verified: Boolean = false,
    val memberSince: LocalDate? = null,
    val tripsRun: Int = 0,
    val rating: RatingSummaryDto = RatingSummaryDto(),
    val reviews: List<ReviewDto> = emptyList(),
)

/**
 * openapi `RatingSummary`: every review of the host's published trips. [average] is absent when
 * [count] is 0. [distribution] is five entries, 5 stars first; read it through [stars], which does not
 * rely on that order.
 */
@Serializable
internal data class RatingSummaryDto(
    val average: Double? = null,
    val count: Int = 0,
    val distribution: List<StarCountDto> = emptyList(),
)

/** One star bucket: how many reviews gave [stars] stars. */
@Serializable
internal data class StarCountDto(
    val stars: Int,
    val count: Int,
)

/** openapi `HostKind`. */
internal object HostKind {
    const val PERSON = "person"
    const val COMPANY = "company"

    /** A value this build does not know is a person (the contract's rule). */
    fun isCompany(kind: String?): Boolean = kind == COMPANY
}

/**
 * The five bars, 5 stars down to 1, each with its count: a bucket the server left out counts 0, and
 * one outside 1..5 is ignored, so the bars are always the same five whatever the payload's order.
 */
internal val RatingSummaryDto.stars: List<StarCountDto>
    get() {
        val counts = distribution.filter { it.stars in MIN_STARS..MAX_STARS }.associate { it.stars to it.count }
        return (MAX_STARS downTo MIN_STARS).map { StarCountDto(stars = it, count = counts[it] ?: 0) }
    }

internal const val MAX_STARS = 5
internal const val MIN_STARS = 1
