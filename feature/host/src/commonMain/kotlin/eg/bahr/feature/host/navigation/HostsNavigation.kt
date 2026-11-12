package eg.bahr.feature.host.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.host.presentation.HostsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/*
 * The hosts directory's public surface (MV-M1): the Hosts tab's root, and the same list pushed from Home's hosts
 * "See all" and search's "See all hosts". A host tapped is the caller's lambda (the host page, beside it in
 * this feature, is wired in the app's NavHost like every other destination).
 */

/** The Hosts tab's root (MV-M1): no Back; a re-tap of the tab scrolls it to the top. */
@Serializable
data object HostsTabRoute

/**
 * The hosts directory pushed in another tab's stack (MV-M1). [q] is search's text ("See all hosts"); [sort] an
 * `listHosts` order (`top_rated` when null, Home's "See all").
 */
@Serializable
data class HostsRoute(
    val q: String? = null,
    val sort: String? = null,
)

/**
 * The hosts directory, as the Hosts tab's root ([HostsTabRoute], no Back, [scrollToTop] on a re-tap) and pushed
 * ([HostsRoute], Back is [onBack]). Either way its search, sort and filters are kept while a host's page opened from
 * it is on top (the view model lives with the back-stack entry). [onHostClick] gets the card's slug, and the name
 * and avatar (a URL) it showed, for the host page's header to draw at once.
 */
fun NavGraphBuilder.hostsScreen(
    onBack: () -> Unit,
    onHostClick: (slug: String, name: String, avatar: String?) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    composable<HostsTabRoute> {
        BahrDestination(this) {
            HostsScreen(initialQ = null, initialSort = null, onBack = null, onHostClick = onHostClick, scrollToTop = scrollToTop)
        }
    }
    composable<HostsRoute> { entry ->
        val route = entry.toRoute<HostsRoute>()
        BahrDestination(this) {
            HostsScreen(initialQ = route.q, initialSort = route.sort, onBack = onBack, onHostClick = onHostClick)
        }
    }
}

/** The hosts directory pushed on top (MV-M1), searching [q] and ordered by [sort] when given. */
fun NavController.navigateToHosts(
    q: String? = null,
    sort: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {},
) = navigate(HostsRoute(q, sort), builder)
