package eg.bahr.core.catalog.model

import kotlinx.serialization.Serializable

/*
 * Wire shapes of the hosts directory, `GET /api/v1/hosts` (`listHosts`, MV-B1), field for field with
 * `../docs/api/openapi.yaml`. Here, not in one feature, because two read it (MV-M1): feature:host's directory pages
 * through it, and feature:search shows the first few matching hosts above the trips. Never a phone number, WhatsApp
 * link or email (as `HostProfile`).
 *
 * Everything the contract does not mark `required` is nullable with a default, and the required ones that are
 * plain values have one too: the backend omits nulls, and one odd field must not fail the list.
 */

/**
 * openapi `HostPage`: a page of the directory; the paging fields are `TripPage`'s. [governorates] are the region
 * chips, served with every page, the same whatever the request narrows to (counts aside).
 */
@Serializable
data class HostPageDto(
    val items: List<HostCardDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val governorates: List<GovernorateChipDto> = emptyList(),
)

/**
 * openapi `HostCard`: one host, opening `getHost` with [slug]. [kind] stays a string (`person` / `company`, see
 * [HostKindKey]) so a value this build does not know reads as a person instead of failing the page. [publishedTrips]
 * is how many trips the host has listed now (not the host page's `tripsRun`). [governorates] are where they go.
 */
@Serializable
data class HostCardDto(
    val slug: String,
    val kind: String = HostKindKey.PERSON,
    val name: String,
    val avatar: ImageDto? = null,
    val verified: Boolean = false,
    val rating: HostCardRatingDto? = null,
    val publishedTrips: Int = 0,
    val governorates: List<GovernorateDto> = emptyList(),
)

/** openapi `HostCard.rating`: the mean (one decimal, the server's) over [count] reviews. Absent with none. */
@Serializable
data class HostCardRatingDto(
    val value: Double,
    val count: Int,
)

/** openapi `Governorate`: [key] is the server's, sent back verbatim as `governorate=`, never built here. */
@Serializable
data class GovernorateDto(
    val key: String,
    val label: String,
)

/**
 * openapi `GovernorateChip`: one region chip. [count] is how many hosts it would show given the rest of the
 * selection; a zero chip is kept, dimmed. [selected] marks the request's `governorate`.
 */
@Serializable
data class GovernorateChipDto(
    val key: String,
    val label: String,
    val count: Int = 0,
    val selected: Boolean = false,
)

/** openapi `HostKind` values, also `listHosts`' `kind` filter. */
object HostKindKey {
    const val PERSON = "person"
    const val COMPANY = "company"

    /** A value this build does not know is a person (the contract's rule). */
    fun isCompany(kind: String?): Boolean = kind == COMPANY
}

/** openapi `listHosts`' `sort` values; [TOP_RATED] is the server's default. */
object HostSortKey {
    const val TOP_RATED = "top_rated"
    const val MOST_TRIPS = "most_trips"
    const val NEWEST = "newest"
}

/**
 * One `listHosts` request's narrowing. Null = not sent (the contract's "leave it out"). [q] goes as typed (the
 * server folds Arabic spelling and case), [category] is a `HomeCategory.key`, [governorate] a chip's key as served.
 */
data class HostListQuery(
    val q: String? = null,
    val sort: String? = null,
    val category: String? = null,
    val governorate: String? = null,
    val kind: String? = null,
)
