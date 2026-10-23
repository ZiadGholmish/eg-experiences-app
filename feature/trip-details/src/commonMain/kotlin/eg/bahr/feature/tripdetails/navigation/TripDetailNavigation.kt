package eg.bahr.feature.tripdetails.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.tripdetails.presentation.TripDetailScreen

/*
 * The trip page's public surface: its destination for the app's NavHost, and how to reach it. What happens
 * *after* the trip page (booking, the host page, every review) is the caller's lambda, so this feature never
 * imports another feature (split from feature:trips, M3-M16).
 *
 * M3-M12: the page moves with the app's one screen transition, which the NavHost sets; it draws on an opaque
 * page ([BahrDestination]), which also hands its scope down for the host row's morph.
 */

/**
 * [onContinue] gets the trip and the picked date (a UUID string, as the contract has it); it is only
 * called for a bookable date.
 *
 * M4-M10: [onHostClick] opens the host's page from the host card (only when the server sent the host's
 * slug), usually feature:host's `navigateToHostProfile`. Between the trip page and the host page the card's
 * row grows into the page's header.
 *
 * M4-M11: [onSeeAllReviews] opens every review of the trip [slug][TripDetailRoute.slug] (with the trip's title)
 * from "See all reviews" under the previews (shown only when the trip counts more reviews than it previews),
 * usually feature:reviews' `navigateToReviews`.
 */
fun NavGraphBuilder.tripDetailScreen(
    onBack: () -> Unit,
    onContinue: (slug: String, departureId: String) -> Unit,
    onHostClick: ((HostLink) -> Unit)? = null,
    onSeeAllReviews: ((slug: String, title: String) -> Unit)? = null,
) {
    composable<TripDetailRoute> { entry ->
        val slug = entry.toRoute<TripDetailRoute>().slug
        BahrDestination(this) {
            TripDetailScreen(
                slug = slug,
                onBack = onBack,
                onContinue = onContinue,
                // Null when the app wires no host page: the card then stays as it was, not tappable.
                onHostClick =
                    onHostClick?.let { open ->
                        { host -> host.slug?.let { open(HostLink(it, host.name, host.avatar?.url)) } }
                    },
                // Null when the app wires no reviews page: no "See all reviews".
                onSeeAllReviews = onSeeAllReviews?.let { open -> { title -> open(slug, title) } },
            )
        }
    }
}

/** What a trip page's host card hands to [tripDetailScreen]'s `onHostClick`: the host's slug and what the card showed. */
data class HostLink(
    val slug: String,
    val name: String? = null,
    val avatar: String? = null,
)

fun NavController.navigateToTripDetail(
    slug: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(TripDetailRoute(slug), builder)
