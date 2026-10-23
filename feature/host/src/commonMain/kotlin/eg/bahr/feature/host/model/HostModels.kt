package eg.bahr.feature.host.model

import eg.bahr.core.catalog.model.ImageDto
import eg.bahr.core.catalog.model.RatingSummaryDto
import eg.bahr.core.catalog.model.ReviewDto
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

/** openapi `HostKind`. */
internal object HostKind {
    const val PERSON = "person"
    const val COMPANY = "company"

    /** A value this build does not know is a person (the contract's rule). */
    fun isCompany(kind: String?): Boolean = kind == COMPANY
}
