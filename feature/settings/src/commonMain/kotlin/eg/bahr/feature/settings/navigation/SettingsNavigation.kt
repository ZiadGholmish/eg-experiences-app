package eg.bahr.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import eg.bahr.core.designsystem.theme.BahrDestination
import eg.bahr.feature.settings.presentation.EditPersonalInfoScreen
import eg.bahr.feature.settings.presentation.ProfileScreen
import eg.bahr.feature.settings.presentation.PublicProfileSettingsScreen
import eg.bahr.feature.settings.presentation.TravellerProfileScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

/** Profile (M3-M0b): the Profile tab's root, which was M4-M8's Settings. */
@Serializable
data object ProfileRoute

/** Edit personal info (M3-M0b), pushed from Profile. */
@Serializable
data object EditPersonalInfoRoute

/**
 * The Profile tab. [appVersion] is the build's version name, which only the app module knows (Android's
 * BuildConfig, iOS's Info.plist). [signInButtons] draws feature:auth's sign-in buttons for the
 * signed-out card; [onEditPersonalInfo] opens [editPersonalInfoScreen]; [deleteAccountSheet] draws
 * feature:auth's delete-account sheet while the row has it open (M3-M0b: sheets, not dialogs). All joined in :composeApp. [scrollToTop] fires when the selected tab is tapped again.
 * [onLikedTrips] (MP-M1) opens the account's liked trips (feature:trip-listing's page). [onPublicProfile] (MP-M2)
 * opens Account → Public profile ([publicProfileSettingsScreen]).
 */
fun NavGraphBuilder.profileScreen(
    appVersion: String,
    signInButtons: @Composable (Modifier) -> Unit,
    onEditPersonalInfo: () -> Unit,
    deleteAccountSheet: @Composable (onDismiss: () -> Unit) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    onLikedTrips: () -> Unit,
    onPublicProfile: () -> Unit,
) {
    composable<ProfileRoute> {
        BahrDestination(this) {
            ProfileScreen(
                appVersion = appVersion,
                signInButtons = signInButtons,
                onEditPersonalInfo = onEditPersonalInfo,
                deleteAccountSheet = deleteAccountSheet,
                scrollToTop = scrollToTop,
                onLikedTrips = onLikedTrips,
                onPublicProfile = onPublicProfile,
            )
        }
    }
}

/** [onBack] leaves unsaved; [onSaved] after a save (Profile, under it, then says "Saved"). */
fun NavGraphBuilder.editPersonalInfoScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    composable<EditPersonalInfoRoute> {
        BahrDestination(this) { EditPersonalInfoScreen(onBack = onBack, onSaved = onSaved) }
    }
}

fun NavController.navigateToEditPersonalInfo() = navigate(EditPersonalInfoRoute) { launchSingleTop = true }

/** Account → Public profile (MP-M2), pushed from Profile. */
@Serializable
data object PublicProfileSettingsRoute

/**
 * A traveller's public profile (MP-M2), by the opaque [publicId] the server hands out (`Review.authorPublicId`,
 * `HomeReview.authorPublicId`, the owner's `ProfileVisibility.publicId`); never an account id.
 */
@Serializable
data class TravellerProfileRoute(
    val publicId: String,
)

/**
 * Account → Public profile (MP-M2): the master switch and the four switches. [onViewProfile] opens the account's own
 * public profile by its id (usually [navigateToTravellerProfile]), offered only while the profile is public.
 */
fun NavGraphBuilder.publicProfileSettingsScreen(
    onBack: () -> Unit,
    onViewProfile: (publicId: String) -> Unit,
) {
    composable<PublicProfileSettingsRoute> {
        BahrDestination(this) { PublicProfileSettingsScreen(onBack = onBack, onViewProfile = onViewProfile) }
    }
}

/**
 * A traveller's public profile (MP-M2), opened from a reviewer's name on the trip page, the host page, all reviews and
 * Home, and from "View my public profile". [onTripClick] opens a trip card's or a review's trip.
 */
fun NavGraphBuilder.travellerProfileScreen(
    onBack: () -> Unit,
    onTripClick: (slug: String) -> Unit,
) {
    composable<TravellerProfileRoute> { entry ->
        val route = entry.toRoute<TravellerProfileRoute>()
        BahrDestination(this) { TravellerProfileScreen(publicId = route.publicId, onBack = onBack, onTripClick = onTripClick) }
    }
}

fun NavController.navigateToPublicProfileSettings() = navigate(PublicProfileSettingsRoute) { launchSingleTop = true }

/** The public profile of [publicId]: what every reviewer name's `onTravellerClick` hands over. */
fun NavController.navigateToTravellerProfile(publicId: String) = navigate(TravellerProfileRoute(publicId))
