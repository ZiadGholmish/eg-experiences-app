package eg.bahr.feature.triplisting.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.triplisting.presentation.CategoryTripsScreen
import eg.bahr.feature.triplisting.presentation.SectionTripsScreen

/*
 * The trip lists' public surface (split from feature:trips, M3-M16): the category page and a Home row's "See all",
 * for the app's NavHost, and how to reach them. A trip tapped in them is the caller's lambda, so this feature never
 * imports another feature.
 *
 * M3-M12: every page here moves with the app's one screen transition, which the NavHost sets; each draws
 * on an opaque page ([BahrDestination]), which also hands its scope down for the header morphs.
 */

/**
 * The category page (M4-M1b). Back is [onBack]; Home under it keeps its scroll position, because its
 * list state is saved with its back-stack entry.
 */
fun NavGraphBuilder.categoryTripsScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
) {
    pageWithHeaderMorph<CategoryTripsRoute> { entry ->
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
) {
    pageWithHeaderMorph<SectionTripsRoute> { entry ->
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
 * A page Home opens (M4-M6): its AnimatedVisibilityScope is handed down for the header morph
 * (`bahrSharedBounds`), on an opaque page ([BahrDestination], M3-M12).
 */
private inline fun <reified T : Any> NavGraphBuilder.pageWithHeaderMorph(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry ->
        BahrDestination(this) { content(entry) }
    }
}

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
