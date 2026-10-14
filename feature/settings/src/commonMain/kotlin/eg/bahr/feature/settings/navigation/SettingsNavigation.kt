package eg.bahr.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eg.bahr.feature.settings.presentation.EditPersonalInfoScreen
import eg.bahr.feature.settings.presentation.ProfileScreen
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
 */
fun NavGraphBuilder.profileScreen(
    appVersion: String,
    signInButtons: @Composable (Modifier) -> Unit,
    onEditPersonalInfo: () -> Unit,
    deleteAccountSheet: @Composable (onDismiss: () -> Unit) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    composable<ProfileRoute> {
        ProfileScreen(
            appVersion = appVersion,
            signInButtons = signInButtons,
            onEditPersonalInfo = onEditPersonalInfo,
            deleteAccountSheet = deleteAccountSheet,
            scrollToTop = scrollToTop,
        )
    }
}

/** [onBack] leaves unsaved; [onSaved] after a save (Profile, under it, then says "Saved"). */
fun NavGraphBuilder.editPersonalInfoScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    composable<EditPersonalInfoRoute> { EditPersonalInfoScreen(onBack = onBack, onSaved = onSaved) }
}

fun NavController.navigateToEditPersonalInfo() = navigate(EditPersonalInfoRoute) { launchSingleTop = true }
