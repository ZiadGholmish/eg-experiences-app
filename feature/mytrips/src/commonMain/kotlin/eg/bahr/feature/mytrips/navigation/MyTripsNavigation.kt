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
 * [onBookingClick] an Upcoming or Past booking's details (MV-M6, feature:booking, wired in :composeApp; the details
 * read whether it may be rated themselves since MV-M13); [onTripClick] the trip page of a waiting-list entry; [onRateTrip] the review page of a finished booking [ref] (M3-M4, feature:reviews, wired in
 * :composeApp). [scrollToTop] fires when the selected tab is tapped again.
 */
fun NavGraphBuilder.myTripsScreen(
    onSignIn: () -> Unit,
    onTripClick: (slug: String) -> Unit,
    onBookingClick: (ref: String) -> Unit = {},
    onRateTrip: (ref: String, title: String?) -> Unit = { _, _ -> },
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    composable<MyTripsRoute> {
        BahrDestination(this) {
            MyTripsScreen(
                onSignIn = onSignIn,
                onTripClick = onTripClick,
                onBookingClick = onBookingClick,
                onRateTrip = onRateTrip,
                scrollToTop = scrollToTop,
            )
        }
    }
}
