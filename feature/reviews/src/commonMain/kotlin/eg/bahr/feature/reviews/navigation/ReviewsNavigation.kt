package eg.bahr.feature.reviews.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.reviews.presentation.ReviewsScreen
import eg.bahr.feature.reviews.presentation.WriteReviewScreen
import kotlinx.serialization.Serializable

/*
 * "See all reviews"' public surface (split from feature:trips, M3-M16): its destination for the app's NavHost, and
 * how to reach it. It opens nothing else. Since M3-M4 also the review page ("Rate your trip"), opened from My trips.
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
 * filters, opened from the trip page or the host page. MP-M2: a reviewer's name opens their public profile
 * ([onTravellerClick]) when the review carries `authorPublicId`; it opens nothing else.
 */
fun NavGraphBuilder.reviewsScreen(
    onBack: () -> Unit,
    onTravellerClick: ((publicId: String) -> Unit)? = null,
) {
    composable<ReviewsRoute> { entry ->
        val route = entry.toRoute<ReviewsRoute>()
        BahrDestination(this) {
            ReviewsScreen(
                trip = route.trip,
                host = route.host,
                title = route.title,
                onBack = onBack,
                onTravellerClick = onTravellerClick,
            )
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

/**
 * Review a trip (M3-M4): booking [ref]'s review, opened from My trips' "Rate your trip" on a finished booking. [title]
 * is the trip's title as My trips showed it (for the heading; the page reads nothing before sending).
 */
@Serializable
data class WriteReviewRoute(
    val ref: String,
    val title: String? = null,
)

/**
 * The review page (M3-M4). [onBack] leaves it, sent or not; [onDone] is "Done" once it is sent. Either way the screen
 * under it (My trips) reads its bookings again on its return, so the review's status shows there.
 */
fun NavGraphBuilder.writeReviewScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    composable<WriteReviewRoute> { entry ->
        val route = entry.toRoute<WriteReviewRoute>()
        BahrDestination(this) {
            WriteReviewScreen(ref = route.ref, title = route.title, onBack = onBack, onDone = onDone)
        }
    }
}

/** The review page of booking [ref] (M3-M4), [title] being its trip's: what My trips' `onRateTrip` hands over. */
fun NavController.navigateToWriteReview(
    ref: String,
    title: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(WriteReviewRoute(ref = ref, title = title), builder)
