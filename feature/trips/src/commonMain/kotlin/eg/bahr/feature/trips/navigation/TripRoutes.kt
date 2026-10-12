package eg.bahr.feature.trips.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe routes for the trips graph. Home's own route is feature:home's (M4-M1c).
 *
 * [TripDetailRoute] keys on the slug rather than the id because the slug is
 * what appears in a shared link — a deep link and an in-app tap then land on
 * exactly the same destination with the same argument.
 */
@Serializable
data class TripDetailRoute(
    val slug: String,
)

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

/**
 * A host's page (M4-M10), opened from the trip page's host card. [name] and [avatar] (a URL) are what
 * the card showed: the header draws them at once, before `getHost` answers (text first), and the
 * card's row has something to morph into.
 */
@Serializable
data class HostProfileRoute(
    val slug: String,
    val name: String? = null,
    val avatar: String? = null,
)

/** Search (M4-M3), opened from Home's search entry. */
@Serializable
data object SearchTripsRoute
