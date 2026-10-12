package eg.bahr.feature.trips.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.ProvideBahrNavScope
import eg.bahr.core.designsystem.theme.bahrBackEnter
import eg.bahr.core.designsystem.theme.bahrBackExit
import eg.bahr.core.designsystem.theme.bahrForwardEnter
import eg.bahr.core.designsystem.theme.bahrForwardExit
import eg.bahr.feature.trips.presentation.CategoryTripsScreen
import eg.bahr.feature.trips.presentation.HostProfileScreen
import eg.bahr.feature.trips.presentation.ReviewsScreen
import eg.bahr.feature.trips.presentation.SearchTripsScreen
import eg.bahr.feature.trips.presentation.SectionTripsScreen
import eg.bahr.feature.trips.presentation.TripDetailScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/*
 * The trips feature's public surface: destinations for the app's NavHost, and how to reach them.
 * What happens *after* a trips screen (booking) is the caller's lambda, so this feature never
 * imports another feature. Home is feature:home's (M4-M1c).
 *
 * M4-M6: the category page, a row's "See all" and search slide in with the shared slide + fade when
 * opened from Home, and leave the same way on Back to it. This feature cannot name Home's route, so
 * each takes `isHome`, which the app answers with feature:home's `isHome()`; Home in turn asks
 * `isOpenedFromHome` which of its destinations get that transition.
 */

/**
 * [onContinue] gets the trip and the picked date (a UUID string, as the contract has it); it is only
 * called for a bookable date.
 *
 * M4-M10: [onHostClick] opens the host's page from the host card (only when the server sent the host's
 * slug), usually [navigateToHostProfile]. Between the trip page and the host page the pages slide with
 * the M4-M6 transition and the card's row grows into the page's header; to and from anything else
 * (booking, a list) the NavHost's own transition applies, as before.
 *
 * M4-M11: [onSeeAllReviews] opens every review of the trip from "See all reviews" under the previews
 * (shown only when the trip counts more reviews than it previews), usually [navigateToReviews]. It
 * slides like the host page.
 */
fun NavGraphBuilder.tripDetailScreen(
    onBack: () -> Unit,
    onContinue: (slug: String, departureId: String) -> Unit,
    onHostClick: ((HostLink) -> Unit)? = null,
    onSeeAllReviews: ((ReviewsRoute) -> Unit)? = null,
    reducedMotion: () -> Boolean = { false },
) {
    composable<TripDetailRoute>(
        // To the host page or the reviews and back.
        exitTransition = { if (targetState.isHostProfile() || targetState.isReviews()) bahrForwardExit(reducedMotion()) else null },
        popEnterTransition = { if (initialState.isHostProfile() || initialState.isReviews()) bahrBackEnter(reducedMotion()) else null },
        // A trip opened from the host page's list, and Back to it.
        enterTransition = { if (initialState.isHostProfile()) bahrForwardEnter(reducedMotion()) else null },
        popExitTransition = { if (targetState.isHostProfile()) bahrBackExit(reducedMotion()) else null },
    ) { entry ->
        val slug = entry.toRoute<TripDetailRoute>().slug
        ProvideBahrNavScope(this) {
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
                onSeeAllReviews = onSeeAllReviews?.let { open -> { title -> open(ReviewsRoute(trip = slug, title = title)) } },
            )
        }
    }
}

/**
 * The host page (M4-M10): the host's profile, rating and reviews, then their trips ([onTripClick]).
 * Opened from a trip page, it slides in from the end side and leaves the same way on Back (M4-M6).
 *
 * M4-M11: [onSeeAllReviews] opens every review of the host's trips (usually [navigateToReviews]), from
 * "See all reviews" under the previews, shown only under a full preview when the summary counts more.
 */
fun NavGraphBuilder.hostProfileScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onSeeAllReviews: ((ReviewsRoute) -> Unit)? = null,
    reducedMotion: () -> Boolean = { false },
) {
    composable<HostProfileRoute>(
        enterTransition = { if (initialState.isTripDetail()) bahrForwardEnter(reducedMotion()) else null },
        popExitTransition = { if (targetState.isTripDetail()) bahrBackExit(reducedMotion()) else null },
        // A trip or the reviews opened from the host page: the host page steps aside the same way, and comes back on Back.
        exitTransition = { if (targetState.isTripDetail() || targetState.isReviews()) bahrForwardExit(reducedMotion()) else null },
        popEnterTransition = { if (initialState.isTripDetail() || initialState.isReviews()) bahrBackEnter(reducedMotion()) else null },
    ) { entry ->
        val route = entry.toRoute<HostProfileRoute>()
        ProvideBahrNavScope(this) {
            HostProfileScreen(
                slug = route.slug,
                name = route.name,
                avatar = route.avatar,
                onBack = onBack,
                onTripClick = onTripClick,
                onSeeAllReviews = onSeeAllReviews?.let { open -> { name -> open(ReviewsRoute(host = route.slug, title = name)) } },
            )
        }
    }
}

/**
 * "See all reviews" (M4-M11): a trip's or a host's reviews, paged, with sort, star and (a host's) Trip
 * filters. Opened from the trip page or the host page, it slides in from the end side and leaves the
 * same way on Back (M4-M6). It opens nothing else.
 */
fun NavGraphBuilder.reviewsScreen(
    onBack: () -> Unit,
    reducedMotion: () -> Boolean = { false },
) {
    composable<ReviewsRoute>(
        enterTransition = { if (initialState.opensReviews()) bahrForwardEnter(reducedMotion()) else null },
        popExitTransition = { if (targetState.opensReviews()) bahrBackExit(reducedMotion()) else null },
    ) { entry ->
        val route = entry.toRoute<ReviewsRoute>()
        ProvideBahrNavScope(this) {
            ReviewsScreen(trip = route.trip, host = route.host, title = route.title, onBack = onBack)
        }
    }
}

/** What a trip page's host card hands to [tripDetailScreen]'s `onHostClick`: the host's slug and what the card showed. */
data class HostLink(
    val slug: String,
    val name: String? = null,
    val avatar: String? = null,
)

private fun NavBackStackEntry.isHostProfile(): Boolean = destination.hasRoute<HostProfileRoute>()

private fun NavBackStackEntry.isTripDetail(): Boolean = destination.hasRoute<TripDetailRoute>()

private fun NavBackStackEntry.isReviews(): Boolean = destination.hasRoute<ReviewsRoute>()

/** The pages with "See all reviews": the reviews page slides to and from them. */
private fun NavBackStackEntry.opensReviews(): Boolean = isTripDetail() || isHostProfile()

/**
 * The category page (M4-M1b). Back is [onBack]; Home under it keeps its scroll position, because its
 * list state is saved with its back-stack entry.
 */
fun NavGraphBuilder.categoryTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    isHome: (NavBackStackEntry) -> Boolean,
    reducedMotion: () -> Boolean = { false },
) {
    openedFromHome<CategoryTripsRoute>(isHome, reducedMotion) { entry ->
        val route = entry.toRoute<CategoryTripsRoute>()
        CategoryTripsScreen(
            category = route.category,
            title = route.title,
            onBack = onBack,
            onTripClick = onTripClick,
            sharedKey = route.sharedKey,
        )
    }
}

/** A Home row's "See all" list (M4-M1b). */
fun NavGraphBuilder.sectionTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    isHome: (NavBackStackEntry) -> Boolean,
    reducedMotion: () -> Boolean = { false },
) {
    openedFromHome<SectionTripsRoute>(isHome, reducedMotion) { entry ->
        val route = entry.toRoute<SectionTripsRoute>()
        SectionTripsScreen(
            sectionId = route.sectionId,
            title = route.title,
            onBack = onBack,
            onTripClick = onTripClick,
            sharedKey = route.sharedKey,
        )
    }
}

/**
 * Search (M4-M3). Back is [onBack]; the search and its results are kept while a trip opened from
 * them is on top (the view model lives with this back-stack entry).
 *
 * M4-M8: the Search tab's root. [onBack] null: no Back. [focusOnEntry] false: the field takes the
 * focus on [focusRequests] (Home's search entry) rather than whenever the tab shows. [scrollToTop]: a
 * re-tap of the selected tab.
 */
fun NavGraphBuilder.searchTripsScreen(
    onBack: (() -> Unit)?,
    onTripClick: (slug: String) -> Unit,
    isHome: (NavBackStackEntry) -> Boolean,
    reducedMotion: () -> Boolean = { false },
    focusOnEntry: Boolean = true,
    focusRequests: Flow<Unit> = emptyFlow(),
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    openedFromHome<SearchTripsRoute>(isHome, reducedMotion) {
        SearchTripsScreen(
            onBack = onBack,
            onTripClick = onTripClick,
            focusOnEntry = focusOnEntry,
            focusRequests = focusRequests,
            scrollToTop = scrollToTop,
        )
    }
}

/**
 * A page opened from Home (M4-M6): it slides in from the end side with a fade, and leaves the same way
 * on Back to Home ([isHome]). To and from any other page (a trip it opens) the NavHost's own transition applies,
 * so the trip page and booking flow are unchanged. Its AnimatedVisibilityScope is handed down for the
 * header morph (`bahrSharedBounds`).
 */
private inline fun <reified T : Any> NavGraphBuilder.openedFromHome(
    noinline isHome: (NavBackStackEntry) -> Boolean,
    noinline reducedMotion: () -> Boolean,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = { if (isHome(initialState)) bahrForwardEnter(reducedMotion()) else null },
        popExitTransition = { if (isHome(targetState)) bahrBackExit(reducedMotion()) else null },
    ) { entry ->
        ProvideBahrNavScope(this) { content(entry) }
    }
}

/**
 * The pages of this feature Home opens with the shared transition: the category page, a row's "See
 * all" and search. The app hands it to Home's `opensFromHome` (M4-M1c: Home cannot name these routes).
 */
fun NavBackStackEntry.isOpenedFromHome(): Boolean =
    destination.hasRoute<CategoryTripsRoute>() || destination.hasRoute<SectionTripsRoute>() || destination.hasRoute<SearchTripsRoute>()

fun NavController.navigateToSearchTrips(builder: NavOptionsBuilder.() -> Unit = {}) = navigate(SearchTripsRoute, builder)

/** [sharedKey]: the Home element that morphs into the page's header (feature:home's `HomeAction.OpenCategory.sharedKey`). */
fun NavController.navigateToCategoryTrips(
    category: String,
    title: String? = null,
    sharedKey: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(CategoryTripsRoute(category, title, sharedKey), builder)

/** [sharedKey]: the Home row title that morphs into the page's heading (feature:home's `HomeAction.OpenSection.sharedKey`). */
fun NavController.navigateToSectionTrips(
    sectionId: String,
    title: String? = null,
    sharedKey: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(SectionTripsRoute(sectionId, title, sharedKey), builder)

/** The host page for [link] (M4-M10). */
fun NavController.navigateToHostProfile(
    link: HostLink,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(HostProfileRoute(link.slug, link.name, link.avatar), builder)

/** "See all reviews" (M4-M11): [route] is what the trip page's or host page's `onSeeAllReviews` hands over. */
fun NavController.navigateToReviews(
    route: ReviewsRoute,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(route, builder)

fun NavController.navigateToTripDetail(
    slug: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(TripDetailRoute(slug), builder)
