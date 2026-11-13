package eg.bahr.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.search.presentation.SearchTripsScreen
import kotlinx.serialization.Serializable

/*
 * Search's public surface (split from feature:trips, M3-M16): its destination for the app's NavHost, and how to
 * reach it. A trip tapped in the results is the caller's lambda, so this feature never imports another feature;
 * so are a matching host and "See all hosts" (MV-M1).
 */

/** Search (M4-M3), opened from Home's search entry. */
@Serializable
data object SearchTripsRoute

/**
 * Search (M4-M3). Back is [onBack]; the search and its results are kept while a trip opened from
 * them is on top (the view model lives with this back-stack entry).
 *
 * Since M3-M0b search is no tab: Home's field and the Map header push it in their own tab, its field
 * takes the focus as it opens, and reopening it shows the last search again (user decision 2026-10-14).
 *
 * M3-M12: it moves with the app's one screen transition, which the NavHost sets; it draws on an opaque page
 * ([BahrDestination]), which also hands its AnimatedVisibilityScope down for the header morph (M4-M6).
 *
 * [onSignIn] (MP-M1) opens the sign-in sheet when a guest taps a trip's heart.
 */
fun NavGraphBuilder.searchTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onHostClick: (host: SearchHost) -> Unit = {},
    onSeeAllHosts: (q: String) -> Unit = {},
    onSignIn: () -> Unit,
) {
    composable<SearchTripsRoute> {
        BahrDestination(this) {
            SearchTripsScreen(
                onBack = onBack,
                onTripClick = onTripClick,
                onHostClick = { host -> onHostClick(SearchHost(host.slug, host.name, host.avatar?.url)) },
                onSeeAllHosts = onSeeAllHosts,
                onSignIn = onSignIn,
            )
        }
    }
}

/**
 * A host card tapped in the results (MV-M1): its [slug], and the [name] and [avatar] (a URL) it showed, for the
 * host page's header to draw before `getHost` answers.
 */
data class SearchHost(
    val slug: String,
    val name: String,
    val avatar: String?,
)

/** Single-top: a quick double tap on Home's field or the Map button opens one search. */
fun NavController.navigateToSearchTrips(builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) =
    navigate(SearchTripsRoute, builder)
