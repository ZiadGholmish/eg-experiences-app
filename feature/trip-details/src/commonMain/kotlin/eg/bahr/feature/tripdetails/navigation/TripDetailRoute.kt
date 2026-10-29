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
    /**
     * A date to open the page on (`YYYY-MM-DD`, M3-M5): the `?date=` of a trip link from an email or a push. Picked if
     * the fresh dates still list it as bookable or sold out; else the page opens with no date picked.
     */
    val date: String? = null,
    /**
     * The link came from a "a place opened" push (`waitlist_seat_opened`, M3-M5): if the fresh dates show [date]
     * bookable, the availability band says "A place opened — book now". Never claimed from the link alone: seat
     * counts are hints, and the place may be gone by the tap.
     */
    val placeOpened: Boolean = false,
)
