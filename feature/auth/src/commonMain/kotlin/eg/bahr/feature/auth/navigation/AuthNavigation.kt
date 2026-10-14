package eg.bahr.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import eg.bahr.feature.auth.presentation.DeleteAccountDialog
import eg.bahr.feature.auth.presentation.SignInScreen
import kotlinx.serialization.Serializable

/** The sign-in sheet (M3-M0), pushed over whatever asked for it; the bar hides under it. */
@Serializable
data object SignInRoute

/** "Delete your account?" with its Apple re-authentication (M3-M0). */
@Serializable
data object DeleteAccountRoute

/**
 * The sign-in sheet. [onDone]: signed in (the profile saved or left for later), usually back to where it
 * was opened from, which now reads the signed-in session itself. [onClose]: left signed out.
 */
fun NavGraphBuilder.signInScreen(
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    composable<SignInRoute> { SignInScreen(onDone = onDone, onClose = onClose) }
}

/** [onDone] once the account is deleted and the session forgotten; [onCancel] when kept. */
fun NavGraphBuilder.deleteAccountDialog(
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    dialog<DeleteAccountRoute> { DeleteAccountDialog(onDone = onDone, onCancel = onCancel) }
}

fun NavController.navigateToSignIn(builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) = navigate(SignInRoute, builder)

fun NavController.navigateToDeleteAccount() = navigate(DeleteAccountRoute) { launchSingleTop = true }
