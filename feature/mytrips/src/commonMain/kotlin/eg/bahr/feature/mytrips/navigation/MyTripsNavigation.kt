package eg.bahr.feature.mytrips.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.mytrips.presentation.MyTripsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/** My trips (M3-M0): the fifth tab's root. */
@Serializable
data object MyTripsRoute

/**
 * The My trips tab. [onSignIn] opens the sign-in sheet (feature:auth, wired in :composeApp);
 * [onTripClick] the trip page; [onRateTrip] the review page of a finished booking [ref] (M3-M4, feature:reviews, wired in
 * :composeApp). [scrollToTop] fires when the selected tab is tapped again.
 */
fun NavGraphBuilder.myTripsScreen(
    onSignIn: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onRateTrip: (ref: String, title: String?) -> Unit = { _, _ -> },
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    composable<MyTripsRoute> {
        BahrDestination(this) {
            MyTripsScreen(onSignIn = onSignIn, onTripClick = onTripClick, onRateTrip = onRateTrip, scrollToTop = scrollToTop)
        }
    }
}
