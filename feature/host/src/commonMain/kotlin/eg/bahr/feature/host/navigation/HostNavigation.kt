package eg.bahr.feature.host.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.host.presentation.HostProfileScreen
import kotlinx.serialization.Serializable

/*
 * The host page's public surface (split from feature:trips, M3-M16): its destination for the app's NavHost, and how
 * to reach it. A trip or "See all reviews" tapped on it is the caller's lambda, so this feature never imports another
 * feature.
 *
 * M3-M12: the page moves with the app's one screen transition, which the NavHost sets; it draws on an opaque page
 * ([BahrDestination]), which also hands its scope down for the header morph.
 */

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

/**
 * The host page (M4-M10): the host's profile, rating and reviews, then their trips ([onTripClick]).
 *
 * M4-M11: [onSeeAllReviews] opens every review of the host's trips (the host's slug and name; usually
 * feature:reviews' `navigateToReviews`), from "See all reviews" under the previews, shown only under a full
 * preview when the summary counts more.
 */
fun NavGraphBuilder.hostProfileScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onSeeAllReviews: ((host: String, name: String) -> Unit)? = null,
) {
    composable<HostProfileRoute> { entry ->
        val route = entry.toRoute<HostProfileRoute>()
        BahrDestination(this) {
            HostProfileScreen(
                slug = route.slug,
                name = route.name,
                avatar = route.avatar,
                onBack = onBack,
                onTripClick = onTripClick,
                onSeeAllReviews = onSeeAllReviews?.let { open -> { name -> open(route.slug, name) } },
            )
        }
    }
}

/** The host page of [slug] (M4-M10), with the [name] and [avatar] the opener already shows (see [HostProfileRoute]). */
fun NavController.navigateToHostProfile(
    slug: String,
    name: String? = null,
    avatar: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(HostProfileRoute(slug, name, avatar), builder)
