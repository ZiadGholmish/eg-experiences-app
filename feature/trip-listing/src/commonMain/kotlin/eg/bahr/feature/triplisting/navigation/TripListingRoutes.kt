package eg.bahr.feature.triplisting.navigation

import kotlinx.serialization.Serializable

/**
 * The category page (M4-M1b): trips of [category], with filter chips. [title] is the label the opener
 * already has, shown until the page's facets name the category (null from a banner). [sharedKey] is
 * the Home element that morphs into the header (M4-M6), null when none does.
 */
@Serializable
data class CategoryTripsRoute(
    val category: String,
    val title: String? = null,
    val sharedKey: String? = null,
)

/**
 * A Home row's "See all": every trip of section [sectionId]. [title] is the row's heading; [sharedKey]
 * the row title that morphs into the page's heading (M4-M6).
 */
@Serializable
data class SectionTripsRoute(
    val sectionId: String,
    val title: String? = null,
    val sharedKey: String? = null,
)
