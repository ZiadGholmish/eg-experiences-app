package eg.bahr.feature.trips.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe routes for the trips graph.
 *
 * [TripDetailRoute] keys on the slug rather than the id because the slug is
 * what appears in a shared link — a deep link and an in-app tap then land on
 * exactly the same destination with the same argument.
 */
@Serializable
data object TripListRoute

@Serializable
data class TripDetailRoute(
    val slug: String,
)

/**
 * The category page (M4-M1b): trips of [category], with filter chips. [title] is the label the opener
 * already has, shown until the page's facets name the category (null from a banner).
 */
@Serializable
data class CategoryTripsRoute(
    val category: String,
    val title: String? = null,
)

/** A Home row's "See all": every trip of section [sectionId]. [title] is the row's heading. */
@Serializable
data class SectionTripsRoute(
    val sectionId: String,
    val title: String? = null,
)
