package eg.bahr.feature.reviews.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.reviews.presentation.ReviewsScreen
import kotlinx.serialization.Serializable

/*
 * "See all reviews"' public surface (split from feature:trips, M3-M16): its destination for the app's NavHost, and
 * how to reach it. It opens nothing else.
 *
 * M3-M12: the page moves with the app's one screen transition, which the NavHost sets, on an opaque page
 * ([BahrDestination]).
 */

/**
 * "See all reviews" (M4-M11): every review of the trip [trip] or of the host [host] (exactly one is
 * set), opened under the review previews of the trip page or the host page. [title] is the trip's title
 * or the host's name, shown under the heading.
 */
@Serializable
data class ReviewsRoute(
    val trip: String? = null,
    val host: String? = null,
    val title: String? = null,
)

/**
 * "See all reviews" (M4-M11): a trip's or a host's reviews, paged, with sort, star and (a host's) Trip
 * filters, opened from the trip page or the host page. It opens nothing else.
 */
fun NavGraphBuilder.reviewsScreen(onBack: () -> Unit) {
    composable<ReviewsRoute> { entry ->
        val route = entry.toRoute<ReviewsRoute>()
        BahrDestination(this) {
            ReviewsScreen(trip = route.trip, host = route.host, title = route.title, onBack = onBack)
        }
    }
}

/**
 * "See all reviews" (M4-M11) of the trip [trip] or the host [host] (exactly one), [title] being the trip's title or
 * the host's name: what the trip page's or host page's `onSeeAllReviews` hands over.
 */
fun NavController.navigateToReviews(
    trip: String? = null,
    host: String? = null,
    title: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(ReviewsRoute(trip = trip, host = host, title = title), builder)
