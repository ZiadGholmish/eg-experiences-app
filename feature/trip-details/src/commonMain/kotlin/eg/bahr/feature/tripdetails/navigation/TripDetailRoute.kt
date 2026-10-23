package eg.bahr.feature.tripdetails.navigation

import kotlinx.serialization.Serializable

/**
 * The trip page's type-safe route.
 *
 * [TripDetailRoute] keys on the slug rather than the id because the slug is
 * what appears in a shared link — a deep link and an in-app tap then land on
 * exactly the same destination with the same argument.
 */
@Serializable
data class TripDetailRoute(
    val slug: String,
)
