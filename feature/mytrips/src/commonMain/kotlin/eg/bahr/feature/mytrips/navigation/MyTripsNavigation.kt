package eg.bahr.feature.mytrips.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eg.bahr.feature.mytrips.presentation.MyTripsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/** My trips (M3-M0): the fifth tab's root. */
@Serializable
data object MyTripsRoute

/**
 * The My trips tab. [onSignIn] opens the sign-in sheet (feature:auth, wired in :composeApp);
 * [onTripClick] the trip page. [scrollToTop] fires when the selected tab is tapped again.
 */
fun NavGraphBuilder.myTripsScreen(
    onSignIn: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    composable<MyTripsRoute> {
        MyTripsScreen(onSignIn = onSignIn, onTripClick = onTripClick, scrollToTop = scrollToTop)
    }
}
